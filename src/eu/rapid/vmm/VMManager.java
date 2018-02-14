package eu.rapid.vmm;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import eu.project.rapid.common.RapidMessages;
import eu.project.rapid.common.RapidUtils;
import eu.rapid.vmm.ThreadPooledServer;

public class VMManager {
	private static VMManager vmManager = new VMManager();
	private Logger logger = Logger.getLogger(getClass());

	static final int preparedImageNums = 10;

	private int VMMPort = 9000;
	private int DSPort = 9001;
	private int maxConnection = 100;
	private String vmmAddress = "127.0.0.1";
	private String dsAddress = "127.0.0.1";

	private String dataPath;
	private String vmImageFolder;

	private String animationAddress = "83.235.169.221";
	private int animationPort = 6666;

	private boolean openStack = true;

	private VMManager() {
		try {
			readConfiguration();
		} catch (Exception e) {
			logger.info("VMM Initialization is failed");
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
			e.printStackTrace();
			// System.exit(-1);
		}
	}

	public static VMManager getInstance() {
		return vmManager;
	}

	public static void main(String[] args) throws Exception {
		VMManager vmManager = VMManager.getInstance();
		VMMEngine vmmEngine = VMMEngine.getInstance();
		LifetimeManager lifetimeManager = LifetimeManager.getInstance();

		vmManager.getLogger().info("Started VMManager Initialization");

		vmManager.getLogger().info("AnimationAddress in VMM is: " + vmManager.getAnimationAddress());

		// VMM is up. temporarily commented.
		//RapidUtils.sendAnimationMsg(vmManager.getAnimationAddress(), vmManager.getAnimationPort(),
		//		RapidMessages.AnimationMsg.VMM_UP.toString());

		// initialize vmTypes. now hard coding, but later will use XML style
		// assignment.
		vmmEngine.initializeVmTypes();

		// restore VM list;
		vmmEngine.restoreVmmInfo();
		lifetimeManager.restoreVmList();

		VmmInfo vmmInfo = vmmEngine.getVmmInfo();

		// normal VM image preparation
		if (!vmManager.isOpenStack() && vmmInfo.getVmImageFreeList().size() < preparedImageNums) {
			vmManager.getLogger().info("Started normal VM image preparation");
			vmManager.copyVMImages(preparedImageNums - vmmInfo.getVmImageFreeList().size());
			vmManager.getLogger().info("Finished normal VM image preparation");
		}

		// Openstack authentication
		if (vmManager.isOpenStack()) {
			lifetimeManager.setOs(lifetimeManager.openStackAuthenticate());

			if (lifetimeManager.getOs() == null) {
				vmManager.getLogger().info("Failed OpenStack Authentication.");
				System.exit(-1);
			}
		}

		int errorCode = RapidMessages.ERROR;
		if (vmmInfo.getVmmId() == 0) {
			if ((errorCode = vmmEngine.vmmRegisterDs()) != RapidMessages.OK) {
				vmManager.getLogger().info("Failed DS registation. errorCode: " + errorCode);
				System.exit(-1);
			} else {
				vmManager.getLogger().info("Finished DS registation. New VMM ID: " + vmmInfo.getVmmId());
			}
		} else {
			// VMM registration temporarily commented.
			//RapidUtils.sendAnimationMsg(vmManager.getAnimationAddress(), vmManager.getAnimationPort(),
			//		RapidMessages.AnimationMsg.VMM_REGISTER_DS.toString());

			vmManager.getLogger().info("Finished DS registation. Existing VMM ID: " + vmmInfo.getVmmId());
		}
		
		if ((errorCode = vmmEngine.vmmRegisterSlam()) != RapidMessages.OK) {
			vmManager.getLogger().info("Failed SLAM registation. errorCode: " + errorCode);
			System.exit(-1);
		}
		vmManager.getLogger().info("Finished SLAM registation");
		// temporarily commented.
		//RapidUtils.sendAnimationMsg(vmManager.getAnimationAddress(), vmManager.getAnimationPort(),
		//		RapidMessages.AnimationMsg.VMM_REGISTER_SLAM.toString());

		// start vmmDsTimer Timer
		VmmDsTimerTask vmmDsTimerTask = new VmmDsTimerTask();
		Timer vmmDsTimer = new Timer();
		vmmDsTimer.schedule(vmmDsTimerTask, 10000, 10000);

		// start VmImageTask thread
		if (!vmManager.isOpenStack()) {
			VmImageTask vmImageTask = new VmImageTask();
			vmImageTask.start();
		}

		vmmEngine.startHelperVms();

		vmManager.getLogger().info("Finished VMManager Initialization");

		try {
			ThreadPooledServer server = new ThreadPooledServer(vmManager.getVMMPort());
			new Thread(server).start();
		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			vmManager.getLogger().error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
			vmManager.getLogger().info("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);

			e.printStackTrace();
		}
	}

	private void readConfiguration() {
		try {
			InputSource is = new InputSource(new FileReader("/home/rapid/rapid_vmm_ds/bin/configuration.xml"));
			Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);

			XPath xpath = XPathFactory.newInstance().newXPath();

			String expression = "//*/VMM";
			NodeList cols = (NodeList) xpath.compile(expression).evaluate(document, XPathConstants.NODESET);

			for (int idx = 0; idx < cols.getLength(); idx++) {
				expression = "//*/vmmPort";
				String vmmPort = xpath.compile(expression).evaluate(document);
				setVMMPort(Integer.parseInt(vmmPort));

				expression = "//*/vmmMaxConnection";
				String vmmMaxConnection = xpath.compile(expression).evaluate(document);
				setMaxConnection(Integer.parseInt(vmmMaxConnection));

				expression = "//*/vmmIpAddress";
				String vmmIpAddress = xpath.compile(expression).evaluate(document);
				setVmmAddress(vmmIpAddress);

				expression = "//*/openStackSupport";
				String openStackSupport = xpath.compile(expression).evaluate(document);
				if (openStackSupport != null && openStackSupport.equals("1"))
					setOpenStack(true);
				else
					setOpenStack(false);

				LifetimeManager lifetimeManager = LifetimeManager.getInstance();

				if (openStack) {
					expression = "//*/defaultFlavorId";
					String defaultFlavorId = xpath.compile(expression).evaluate(document);
					lifetimeManager.setDefaultFlavorId(defaultFlavorId);
					
					expression = "//*/type2_1024_20FlavorId";
					String type2_1024_20FlavorId = xpath.compile(expression).evaluate(document);
					lifetimeManager.setType2_1024_20FlavorId(type2_1024_20FlavorId);
					
					expression = "//*/type4_1024_20FlavorId";
					String type4_1024_20FlavorId = xpath.compile(expression).evaluate(document);
					lifetimeManager.setType4_1024_20FlavorId(type4_1024_20FlavorId);

					expression = "//*/linuxNormalVmImageId";
					String linuxNormalVmImageId = xpath.compile(expression).evaluate(document);
					lifetimeManager.setLinuxNormalVmImageId(linuxNormalVmImageId);

					expression = "//*/linuxHelperVmImageId";
					String linuxHelperVmImageId = xpath.compile(expression).evaluate(document);
					lifetimeManager.setLinuxHelperVmImageId(linuxHelperVmImageId);

					expression = "//*/androidNormalVmImageId";
					String androidNormalVmImageId = xpath.compile(expression).evaluate(document);
					lifetimeManager.setAndroidNormalVmImageId(androidNormalVmImageId);

					expression = "//*/androidHelperVmImageId";
					String androidHelperVmImageId = xpath.compile(expression).evaluate(document);
					lifetimeManager.setAndroidHelperVmImageId(androidHelperVmImageId);

					expression = "//*/authAddress";
					String authAddress = xpath.compile(expression).evaluate(document);
					lifetimeManager.setAuthAddress(authAddress);

					expression = "//*/openStackUserId";
					String openStackUserId = xpath.compile(expression).evaluate(document);
					lifetimeManager.setOpenStackUserId(openStackUserId);

					expression = "//*/openStackPasswd";
					String openStackPasswd = xpath.compile(expression).evaluate(document);
					lifetimeManager.setOpenStackPasswd(openStackPasswd);

					expression = "//*/projectId1";
					String projectId1 = xpath.compile(expression).evaluate(document);
					lifetimeManager.setProjectId1(projectId1);

					expression = "//*/projectId2";
					String projectId2 = xpath.compile(expression).evaluate(document);
					lifetimeManager.setProjectId2(projectId2);

					expression = "//*/networkId";
					String networkId = xpath.compile(expression).evaluate(document);
					lifetimeManager.setNetworkId(networkId);

					expression = "//*/publicKey";
					String publicKey = xpath.compile(expression).evaluate(document);
					lifetimeManager.setPublicKey(publicKey);

					expression = "//*/offloadingSecurity";
					String offloadingSecurity = xpath.compile(expression).evaluate(document);
					lifetimeManager.setOffloadingSecurity(offloadingSecurity);

				} else {
					expression = "//*/normalVmImagePath";
					String normalVmImagePath = xpath.compile(expression).evaluate(document);
					lifetimeManager.setPathToNoramlVmImage(normalVmImagePath);

					expression = "//*/helperVmImagePath1";
					String helperVmImagePath1 = xpath.compile(expression).evaluate(document);
					lifetimeManager.setPathToHelperVmImage1(helperVmImagePath1);

					expression = "//*/helperVmImagePath2";
					String helperVmImagePath2 = xpath.compile(expression).evaluate(document);
					lifetimeManager.setPathToHelperVmImage2(helperVmImagePath2);

					expression = "//*/vmImageFolder";
					vmImageFolder = xpath.compile(expression).evaluate(document);
				}

				expression = "//*/gpuCores";
				String gpuCores = xpath.compile(expression).evaluate(document);
				VMMEngine vmmEngine = VMMEngine.getInstance();
				vmmEngine.setGpuCores(Integer.parseInt(gpuCores));

				expression = "//*/availableType";
				String availableType = xpath.compile(expression).evaluate(document);
				vmmEngine.setAvailableType(availableType);

				int[] availableTypeArray = new int[10];
				for (int i = 0; i < 10; i++) {
					availableTypeArray[i] = Integer.parseInt(availableType.substring(i, i + 1));
				}
				vmmEngine.setAvailableTypeArray(availableTypeArray);

				expression = "//*/nvidiaSmiPath";
				String nvidiaSmiPath = xpath.compile(expression).evaluate(document);
				ResourceMonitor resourceMonitor = ResourceMonitor.getInstance();
				resourceMonitor.setNvidiaSmiPath(nvidiaSmiPath);

				expression = "//*/gpuDeviceNum";
				String gpuDeviceNum = xpath.compile(expression).evaluate(document);
				resourceMonitor.setGpuDeviceNum(Integer.parseInt(gpuDeviceNum));

				expression = "//*/dataPath";
				String dataPath = xpath.compile(expression).evaluate(document);
				setDataPath(dataPath);

				expression = "//*/ipShellPath";
				String ipShellPath = xpath.compile(expression).evaluate(document);
				lifetimeManager.setIpShellPath(ipShellPath);

				expression = "//*/vmShellPath";
				String vmShellPath = xpath.compile(expression).evaluate(document);
				lifetimeManager.setVmShellPath(vmShellPath);
			}

			expression = "//*/DS";
			cols = (NodeList) xpath.compile(expression).evaluate(document, XPathConstants.NODESET);

			for (int idx = 0; idx < cols.getLength(); idx++) {
				expression = "//*/dsPort";
				String dsPort = xpath.compile(expression).evaluate(document);
				setDSPort(Integer.parseInt(dsPort));

				expression = "//*/dsIpAddress";
				String dsIpAddress = xpath.compile(expression).evaluate(document);
				setDsAddress(dsIpAddress);
			}
		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);

			e.printStackTrace();
		}
	}

	/**
	 * This functions copies the template VM image for copyNum times.
	 * 
	 * @param copyNum
	 */
	public void copyVMImages(int copyNum) {
		LifetimeManager lifetimeManager = LifetimeManager.getInstance();
		VMMEngine vmmEngine = VMMEngine.getInstance();

		String pathToNoramlVmImage = lifetimeManager.getPathToNoramlVmImage();

		for (int i = 0; i < copyNum; i++) {
			File from = new File(pathToNoramlVmImage);
			String toImage = vmImageFolder + "/RAPID-VM-" + UUID.randomUUID();
			File to = new File(toImage);

			try {
				logger.info("Started copying image from: " + pathToNoramlVmImage + " to: " + toImage);
				copyFile(from, to);
				logger.info("Finished copying image from: " + pathToNoramlVmImage + " to: " + toImage);
				List<String> vmImageFreeList = vmmEngine.getVmmInfo().getVmImageFreeList();
				synchronized (vmImageFreeList) {
					vmImageFreeList.add(toImage);
				}
				vmmEngine.saveVmmInfo();
			} catch (Exception e) {
				String message = "";
				for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
					message = message + System.lineSeparator() + stackTraceElement.toString();
				}
				logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
				e.printStackTrace();
			}
		}
	}

	private void copyFile(File from, File to) throws IOException {
		Files.copy(from.toPath(), to.toPath());
	}

	/**
	 * @return
	 */
	public int getVMMPort() {
		return VMMPort;
	}

	/**
	 * @return
	 */
	public Logger getLogger() {
		return logger;
	}

	/**
	 * @param logger
	 */
	public void setLogger(Logger logger) {
		this.logger = logger;
	}

	/**
	 * @param vMMPort
	 */
	public void setVMMPort(int vMMPort) {
		VMMPort = vMMPort;
	}

	/**
	 * @return
	 */
	public int getDSPort() {
		return DSPort;
	}

	/**
	 * @param dSPort
	 */
	public void setDSPort(int dSPort) {
		DSPort = dSPort;
	}

	/**
	 * @return
	 */
	public int getMaxConnection() {
		return maxConnection;
	}

	/**
	 * @param maxConnection
	 */
	public void setMaxConnection(int maxConnection) {
		this.maxConnection = maxConnection;
	}

	/**
	 * @return
	 */
	public String getVmmAddress() {
		return vmmAddress;
	}

	/**
	 * @param vmmAddress
	 */
	public void setVmmAddress(String vmmAddress) {
		this.vmmAddress = vmmAddress;
	}

	/**
	 * @return
	 */
	public String getDsAddress() {
		return dsAddress;
	}

	/**
	 * @param dsAddress
	 */
	public void setDsAddress(String dsAddress) {
		this.dsAddress = dsAddress;
	}

	/**
	 * @return
	 */
	public String getDataPath() {
		return dataPath;
	}

	/**
	 * @param dataPath
	 */
	public void setDataPath(String dataPath) {
		this.dataPath = dataPath;
	}

	/**
	 * @return
	 */
	public String getVmImageFolder() {
		return vmImageFolder;
	}

	/**
	 * @param vmImageFolder
	 */
	public void setVmImageFolder(String vmImageFolder) {
		this.vmImageFolder = vmImageFolder;
	}

	/**
	 * @return the animationAddress
	 */
	public String getAnimationAddress() {
		return animationAddress;
	}

	/**
	 * @param animationAddress
	 *            the animationAddress to set
	 */
	public void setAnimationAddress(String animationAddress) {
		this.animationAddress = animationAddress;
	}

	/**
	 * @return the animationPort
	 */
	public int getAnimationPort() {
		return animationPort;
	}

	/**
	 * @param animationPort
	 *            the animationPort to set
	 */
	public void setAnimationPort(int animationPort) {
		this.animationPort = animationPort;
	}

	/**
	 * @return the openStack
	 */
	public boolean isOpenStack() {
		return openStack;
	}

	/**
	 * @param openStack
	 *            the openStack to set
	 */
	public void setOpenStack(boolean openStack) {
		this.openStack = openStack;
	}
}

class VmmDsTimerTask extends TimerTask {
	public void run() {
		VMMEngine vmmEngine = VMMEngine.getInstance();
		vmmEngine.vmmNotifyDs();
	}
}

class VmImageTask extends Thread {
	public void run() {
		VMMEngine vmmEngine = VMMEngine.getInstance();
		VMManager vmManager = VMManager.getInstance();

		VmmInfo vmmInfo = vmmEngine.getVmmInfo();

		do {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			if (vmmInfo.getVmImageFreeList().size() <= VMManager.preparedImageNums / 2)
				vmManager.copyVMImages(VMManager.preparedImageNums - vmmInfo.getVmImageFreeList().size());

		} while (true);

	}
}
