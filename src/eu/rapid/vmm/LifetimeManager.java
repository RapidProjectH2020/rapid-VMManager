package eu.rapid.vmm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.compute.FlavorService;
import org.openstack4j.model.common.ActionResponse;
import org.openstack4j.model.common.Identifier;
import org.openstack4j.model.compute.Action;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.network.IP;
import org.openstack4j.model.network.Port;
import org.openstack4j.model.network.SecurityGroup;

import eu.project.rapid.common.RapidConstants;
import eu.project.rapid.common.RapidMessages;
import eu.rapid.vmmanager.api.RapidVMManager;
import eu.rapid.vmmanager.api.datamodel.Flavor;
import eu.rapid.vmmanager.api.datamodel.VMRequest;
import eu.rapid.vmmanager.api.impl.RapidVMManagerImpl;

public class LifetimeManager {
	private static LifetimeManager lifetimeManager = new LifetimeManager();
	private Logger logger = Logger.getLogger(getClass());

	/* for Libvirt */
	private String pathToNoramlVmImage;
	private String pathToHelperVmImage1;
	private String pathToHelperVmImage2;

	/* for Openstack */
	private String defaultFlavorId;
	private String type2_1024_20FlavorId;
	private String type4_1024_20FlavorId;
	private String linuxNormalVmImageId;
	private String linuxHelperVmImageId;
	private String androidNormalVmImageId;
	private String androidHelperVmImageId;
	private String authAddress;
	private String openStackUserId;
	private String openStackPasswd;
	private String projectId1;
	private String projectId2;
	private String networkId;
	private String publicKey;
	private String offloadingSecurity;
	private OSClient.OSClientV3 os;

	private String ipShellPath;
	private String vmShellPath;

	private List<VmInfo> vmList;

	private LifetimeManager() {
	}

	public static LifetimeManager getInstance() {
		return lifetimeManager;
	}

	/**
	 * This function saves the local VM information into the vmList.out file for
	 * persistence.
	 * 
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public synchronized void restoreVmList() throws Exception {
		VMManager vmManager = VMManager.getInstance();
		try {
			File file = new File(vmManager.getDataPath() + "/vmList.out");
			if (file.exists() && !file.isDirectory()) {
				FileInputStream fis = new FileInputStream(vmManager.getDataPath() + "/vmList.out");
				ObjectInputStream ois = new ObjectInputStream(fis);
				vmList = (List<VmInfo>) ois.readObject();
				fis.close();
			} else {
				vmList = Collections.synchronizedList(new ArrayList<VmInfo>());
			}


			RapidVMManager t = new RapidVMManagerImpl();
			Iterator<VmInfo> iterator = vmList.iterator();
			/* temporarily commented */
			/*
			while (iterator.hasNext()) {
				VmInfo vmInfo = (VmInfo) iterator.next();

				// check whether the VM is still active.
				String pid;
				if (vmManager.isOpenStack())
					pid = t.getVmIp(vmShellPath, vmInfo.getVmIdByLM());
				else
					pid = t.getVmIp(vmShellPath, vmInfo.getVmName());

				if (pid == null || pid.trim().isEmpty() || pid.equals("null")) {
					iterator.remove();
					logger.info("Invalid VM information: " + vmInfo.getVmName());

					if (!vmManager.isOpenStack() && vmInfo.getHelperVmNum() == 0) {
						File vmImage = new File(vmInfo.getVmImagePath());
						if (vmImage.delete())
							logger.info("Deleted vm image: " + vmInfo.getVmImagePath());
						else
							logger.info("Failed VM image deletion: " + vmInfo.getVmImagePath());
					}

					// notify the DS.
					Socket vmmDsSocket = new Socket(vmManager.getDsAddress(), vmManager.getDSPort());
					ObjectOutputStream dsOut = new ObjectOutputStream(vmmDsSocket.getOutputStream());
					dsOut.flush();
					ObjectInputStream dsIn = new ObjectInputStream(vmmDsSocket.getInputStream());

					dsOut.writeByte(RapidMessages.VM_NOTIFY_DS);

					dsOut.writeLong(vmInfo.getVmid());
					dsOut.writeInt(VmInfo.VM_STOPPED); // vmstatus
					dsOut.flush();

					dsOut.close();
					dsIn.close();
					vmmDsSocket.close();
				}
			}
			*/

			saveVmList();

			logger.info("VMM database List : total " + vmList.size() + " items");
			int i = 0;
			iterator = vmList.iterator();
			while (iterator.hasNext()) {
				VmInfo vmInfo = (VmInfo) iterator.next();

				String osType = "";
				if (vmInfo.getOsType() == RapidConstants.OS.valueOf("LINUX").ordinal())
					osType = "Linux";
				if (vmInfo.getOsType() == RapidConstants.OS.valueOf("ANDROID").ordinal())
					osType = "Android";

				logger.info(++i + ") local vmId: " + vmInfo.getVmIdByLM() + ", DS vmId: " + vmInfo.getVmid()
						+ ", userId: " + vmInfo.getUserid() + ", ipAddress: " + vmInfo.getIpAddress() + ", status: "
						+ vmInfo.getStatus() + ", osType: " + osType + ", helper VM: "
						+ (vmInfo.getHelperVmNum() > 0 ? "Yes" : "No"));
			}
		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
			e.printStackTrace();
			throw new Exception();
		}
	}

	/**
	 * This function restores the local VM information from the vmList.out file.
	 * 
	 * @throws Exception
	 */
	public synchronized void saveVmList() throws Exception {
		VMManager vmManager = VMManager.getInstance();
		try {
			FileOutputStream fos = new FileOutputStream(vmManager.getDataPath() + "/vmList.out", false);
			ObjectOutputStream oos = new ObjectOutputStream(fos);

			oos.writeObject(vmList);
			oos.flush();
			fos.close();
		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
			e.printStackTrace();
			throw new Exception();
		}
	}

	public OSClient.OSClientV3 openStackAuthenticate() {
		RapidVMManager rapidVMManager = new RapidVMManagerImpl();

		OSClient.OSClientV3 os = rapidVMManager.authenticateExternalCloud(authAddress, openStackUserId, openStackPasswd,
				Identifier.byId(projectId1), Identifier.byId(projectId2));

		return os;
	}

	/**
	 * This function creates a VM for libvirt, allocating resources according to
	 * the parameters.
	 * 
	 * @param userid
	 *            user ID obtained from the DS.
	 * @param memory
	 *            requested memory size in KB.
	 * @param numCore
	 *            requested number of cores.
	 * @param disk
	 *            requested disk size in GB.
	 * @param helperVmNum
	 *            helper VM number (1 or 2). If the created VM is a normal VM,
	 *            this parameter should be 0.
	 * @return VM information object
	 * @throws Exception
	 */
	public VmInfo createVM(long userid, long memory, int numCore, int disk, int helperVmNum) throws Exception {
		VmInfo existVmInfo = getVmInfoByUserId(userid);

		if (existVmInfo != null) {
			return existVmInfo;
		}

		try {
			String vmImagePath = null;
			RapidVMManager t = new RapidVMManagerImpl();
			VMRequest vr = new VMRequest();
			vr.setFlavorType(Flavor.FlavorType.standard);
			vr.setCores(numCore);
			vr.setMemory(memory);
			vr.setDisk(disk * 1024);
			if (helperVmNum == 1 || helperVmNum == 2) {
				if (helperVmNum == 1) {
					vmImagePath = pathToHelperVmImage1;
					vr.setImagePath(pathToHelperVmImage1);
				}
				if (helperVmNum == 2) {
					vmImagePath = pathToHelperVmImage2;
					vr.setImagePath(pathToHelperVmImage2);
				}
			} else {
				VMMEngine vmmEngine = VMMEngine.getInstance();

				List<String> vmImageFreeList = vmmEngine.getVmmInfo().getVmImageFreeList();
				if (vmImageFreeList.size() == 0)
					return null;

				synchronized (vmImageFreeList) {
					vmImagePath = vmImageFreeList.remove(0);
				}
				vmmEngine.saveVmmInfo();

				vr.setImagePath(vmImagePath);
			}
			String vmName = t.createVM(vr);

			if (vmName.equals(""))
				return null;

			String vmIdByLM = vmName.substring(15);

			VmInfo vmInfo = new VmInfo();
			vmInfo.setVmName(vmName);
			vmInfo.setUserid(userid);
			vmInfo.setVmIdByLM(vmIdByLM);
			vmInfo.setStatus(VmInfo.VM_STARTED);
			vmInfo.setHelperVmNum(helperVmNum);
			vmInfo.setVmImagePath(vmImagePath);

			int sleepCount = 0;
			String ipAddress = null;
			do {
				sleepCount++;
				Thread.sleep(1 * 1000);

				ipAddress = getIp(vmInfo);

				if (ipAddress != null && !ipAddress.equals("null")) {
					logger.info("VM ipAddress: " + ipAddress);
					vmInfo.setIpAddress(ipAddress);
					break;
				}

			} while (sleepCount <= 100);

			synchronized (vmList) {
				vmList.add(vmInfo);
			}

			saveVmList();

			return vmInfo;

		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
			e.printStackTrace();
			throw new Exception();
		}
	}

	/**
	 * This function creates a VM for openstack, allocating resources according
	 * to the parameters.
	 * 
	 * @param userid
	 *            user ID obtained from the DS.
	 * @param flavorId
	 *            flavorId defined in the openstack infrastructure.
	 * @param helperVmNum
	 *            helper VM number (1 or 2). If the created VM is a normal VM,
	 *            this parameter should be 0.
	 * @return VM information object
	 * @throws Exception
	 */
	public VmInfo createVM(long userid, String flavorId, int helperVmNum, int osType) throws Exception {
		VmInfo existVmInfo = getVmInfoByUserId(userid);

		if (existVmInfo != null) {
			return existVmInfo;
		}

		lifetimeManager.openStackAuthenticate();

		try {
			RapidVMManager t = new RapidVMManagerImpl();

			String vmName = "";
			String result = "";
			String vmImageId = "";
			RapidConstants.OS linux = RapidConstants.OS.valueOf("LINUX");
			RapidConstants.OS android = RapidConstants.OS.valueOf("ANDROID");

			if (helperVmNum == 1 || helperVmNum == 2) {
				if (osType == linux.ordinal()) {
					vmName = "RAPID-LinuxHelperVM-" + helperVmNum;
					vmImageId = linuxHelperVmImageId;
					result = t.createExternalVM(os, vmName, flavorId, linuxHelperVmImageId, networkId, publicKey);
					logger.info("createExternalVM. HelperVM ID: " + result);
				}
				if (osType == android.ordinal()) {
					vmName = "RAPID-AndroidHelperVM-" + helperVmNum;
					vmImageId = androidHelperVmImageId;
					result = t.createExternalVM(os, vmName, flavorId, androidHelperVmImageId, networkId, publicKey);
					logger.info("createExternalVM. HelperVM ID: " + result);
				}

			} else {
				if (osType == linux.ordinal()) {
					vmName = "RAPID-LinuxNormalVM-userId-" + userid;
					vmImageId = linuxNormalVmImageId;
					result = t.createExternalVM(os, vmName, flavorId, linuxNormalVmImageId, networkId, publicKey);
					logger.info("createExternalVM. NormalVM ID: " + result);
				}
				if (osType == android.ordinal()) {
					vmName = "RAPID-AndroidNormalVM-userId-" + userid;
					vmImageId = androidNormalVmImageId;
					result = t.createExternalVM(os, vmName, flavorId, androidNormalVmImageId, networkId, publicKey);
					logger.info("createExternalVM. NormalVM ID: " + result);
				}
			}

			if (result.equals(""))
				return null;

			String vmIdByLM = result.substring(9);
			logger.info("createExternalVM. vmIdByLM ID: " + vmIdByLM);

			VmInfo vmInfo = new VmInfo();
			vmInfo.setVmName(vmName);
			vmInfo.setUserid(userid);
			vmInfo.setVmIdByLM(vmIdByLM);
			vmInfo.setStatus(VmInfo.VM_STARTED);
			vmInfo.setHelperVmNum(helperVmNum);
			vmInfo.setOsType(osType);
			vmInfo.setVmImageId(vmImageId);

			Server openStackServer = os.compute().servers().get(vmIdByLM);
			String serverId = openStackServer.getId();
			
			logger.info("getAccessIPv4 ipAddress: " + openStackServer.getAccessIPv4());
			logger.info("getAccesses ipAddress: " + openStackServer.getAddresses().toString());

			int sleepCount = 0;
			String ipAddress = null;
			
			do {
				List<? extends Port> ports = os.networking().port().list();
				for (Port port : ports) {
					if (port.getDeviceId().equals(serverId)) {
						Set<? extends IP> ips = port.getFixedIps();
						for (IP ip : ips) {
							ipAddress = ip.getIpAddress();
							break;
						}
						break;
					}
				}

				if (ipAddress != null && !ipAddress.equals("") && !ipAddress.equals("null")) {
					logger.info("VM ipAddress: " + ipAddress);
					vmInfo.setIpAddress(ipAddress);
					break;
				}

				sleepCount++;
				Thread.sleep(1 * 1000);
			} while (sleepCount <= 30);

			/* subscribe to offloading security group */
			os.compute().servers().addSecurityGroup(serverId, offloadingSecurity);

			synchronized (vmList) {
				vmList.add(vmInfo);
			}

			saveVmList();

			return vmInfo;

		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
			e.printStackTrace();
			throw new Exception();
		}
	}

	/**
	 * This function suspends the VM based on the user ID.
	 * 
	 * @param userid
	 *            user ID obtained from the DS.
	 * @return a boolean value that returns success or fail.
	 * @throws Exception
	 */
	public boolean suspendVM(long userid) throws Exception {
		VMManager vmManager = VMManager.getInstance();
		try {
			VmInfo vmInfo = getVmInfoByUserId(userid);

			if (vmInfo == null)
				return false;

			if (vmInfo.getStatus() != VmInfo.VM_STARTED && vmInfo.getStatus() != VmInfo.VM_RESUMED)
				return false;

			RapidVMManager t = new RapidVMManagerImpl();

			boolean result = false;
			if (!vmManager.isOpenStack()) {
				result = t.suspendVM(vmInfo.getVmIdByLM());
			} else {
				lifetimeManager.openStackAuthenticate();
				result = t.executeExternalVMAction(os, vmInfo.getVmIdByLM(), Action.SUSPEND);
			}

			if (result == true) {
				vmInfo.setStatus(VmInfo.VM_SUSPENDED);
				saveVmList();
			}
			return result;
		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
			e.printStackTrace();
			throw new Exception();
		}
	}

	/**
	 * This function resumes the suspended VM based on the user ID.
	 * 
	 * @param userid
	 *            user ID obtained from the DS.
	 * @return a boolean value that returns success or fail.
	 * @throws Exception
	 */
	public boolean resumeVM(long userid) throws Exception {
		VMManager vmManager = VMManager.getInstance();
		try {
			VmInfo vmInfo = getVmInfoByUserId(userid);

			if (vmInfo == null)
				return false;

			if (vmInfo.getStatus() != VmInfo.VM_SUSPENDED)
				return false;

			RapidVMManager t = new RapidVMManagerImpl();

			boolean result = false;
			if (!vmManager.isOpenStack()) {
				result = t.resumeVM(vmInfo.getVmIdByLM());
			} else {
				lifetimeManager.openStackAuthenticate();
				result = t.executeExternalVMAction(os, vmInfo.getVmIdByLM(), Action.RESUME);
			}

			if (result == true) {
				vmInfo.setStatus(VmInfo.VM_RESUMED);
				saveVmList();
			}
			return result;
		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
			e.printStackTrace();
			throw new Exception();
		}
	}

	/**
	 * This function removes the requested VM.
	 * 
	 * @param userid
	 *            user ID obtained from the DS.
	 * @return a boolean value that returns success or fail.
	 * @throws Exception
	 */
	public boolean removeVM(long userid) throws Exception {
		VMManager vmManager = VMManager.getInstance();
		try {
			VmInfo vmInfo = getVmInfoByUserId(userid);

			if (vmInfo == null)
				return false;

			RapidVMManager t = new RapidVMManagerImpl();

			boolean result = false;
			if (!vmManager.isOpenStack()) {
				result = t.deleteVM(vmInfo.getVmIdByLM());

				File file = new File(vmInfo.getVmImagePath());
				if (file.delete())
					logger.info("Deleted VM image: " + vmInfo.getVmImagePath());
				else
					logger.info("Failed VM image deletion: " + vmInfo.getVmImagePath());
			} else {
				lifetimeManager.openStackAuthenticate();
				ActionResponse actionResponse = t.deleteExternalVM(os, vmInfo.getVmIdByLM());
				
				FlavorService flavorservice = os.compute().flavors();
				if (vmInfo.getFlavor() != null)
					flavorservice.delete(vmInfo.getFlavor().getId());

				if (actionResponse != null && actionResponse.getCode() == 200)
					result = true;
			}

			synchronized (vmList) {
				vmList.remove(vmInfo);
			}
			saveVmList();

			return result;
		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
			e.printStackTrace();
			throw new Exception();
		}
	}

	/**
	 * This function returns the IP address of the VM associated with the user
	 * ID.
	 * 
	 * @param userid
	 *            user ID obtained from the DS.
	 * @return IP address.
	 * @throws Exception
	 */
	public String getIpByUserId(long userid) throws Exception {
		String ipAddress;
		try {
			VmInfo vmInfo = getVmInfoByUserId(userid);

			if (vmInfo == null)
				return "";

			RapidVMManager t = new RapidVMManagerImpl();
			ipAddress = t.getVmIp(ipShellPath, vmInfo.getVmName());
			return ipAddress;
		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
			e.printStackTrace();
			throw new Exception();
		}
	}

	/**
	 * This function returns the IP address of a VM.
	 * 
	 * @param vmInfo
	 *            VM information
	 * @return IP address
	 * @throws Exception
	 */
	public String getIp(VmInfo vmInfo) throws Exception {
		String ipAddress;
		try {
			if (vmInfo == null)
				return "";

			RapidVMManager t = new RapidVMManagerImpl();
			ipAddress = t.getVmIp(ipShellPath, vmInfo.getVmName());
			return ipAddress;
		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
			e.printStackTrace();
			throw new Exception();
		}
	}

	/**
	 * This function returns the VM ID of the VM associated with the user ID.
	 * 
	 * @param userid
	 *            user ID obtained from the DS.
	 * @return VM ID.
	 */
	public String getVmIdByUserId(long userid) {
		VmInfo vmInfo = getVmInfoByUserId(userid);
		if (vmInfo != null)
			return vmInfo.getVmIdByLM();
		else
			return "";
	}

	/**
	 * This function returns the VM information of the VM associated with the
	 * user ID.
	 * 
	 * @param userid
	 *            user ID obtained from the DS.
	 * @return VM information.
	 */
	public VmInfo getVmInfoByUserId(long userid) {
		VmInfo vmInfo = null;
		Iterator<VmInfo> iterator = vmList.iterator();
		while (iterator.hasNext()) {
			VmInfo tempVm = (VmInfo) iterator.next();
			if (tempVm.getUserid() == userid) {
				vmInfo = tempVm;
				break;
			}
		}
		return vmInfo;
	}

	/**
	 * @return
	 */
	public String getPathToNoramlVmImage() {
		return pathToNoramlVmImage;
	}

	/**
	 * @param pathToNoramlVmImage
	 */
	public void setPathToNoramlVmImage(String pathToNoramlVmImage) {
		this.pathToNoramlVmImage = pathToNoramlVmImage;
	}

	/**
	 * @return
	 */
	public String getPathToHelperVmImage1() {
		return pathToHelperVmImage1;
	}

	/**
	 * @param pathToHelperVmImage1
	 */
	public void setPathToHelperVmImage1(String pathToHelperVmImage1) {
		this.pathToHelperVmImage1 = pathToHelperVmImage1;
	}

	/**
	 * @return
	 */
	public String getPathToHelperVmImage2() {
		return pathToHelperVmImage2;
	}

	/**
	 * @param pathToHelperVmImage2
	 */
	public void setPathToHelperVmImage2(String pathToHelperVmImage2) {
		this.pathToHelperVmImage2 = pathToHelperVmImage2;
	}

	/**
	 * @return the defaultFlavorId
	 */
	public String getDefaultFlavorId() {
		return defaultFlavorId;
	}

	/**
	 * @param defaultFlavorId
	 *            the defaultFlavorId to set
	 */
	public void setDefaultFlavorId(String defaultFlavorId) {
		this.defaultFlavorId = defaultFlavorId;
	}

	public String getType2_1024_20FlavorId() {
		return type2_1024_20FlavorId;
	}

	public void setType2_1024_20FlavorId(String type2_1024_20FlavorId) {
		this.type2_1024_20FlavorId = type2_1024_20FlavorId;
	}

	public String getType4_1024_20FlavorId() {
		return type4_1024_20FlavorId;
	}

	public void setType4_1024_20FlavorId(String type4_1024_20FlavorId) {
		this.type4_1024_20FlavorId = type4_1024_20FlavorId;
	}

	/**
	 * @return the linuxNormalVmImageId
	 */
	public String getLinuxNormalVmImageId() {
		return linuxNormalVmImageId;
	}

	/**
	 * @param linuxNormalVmImageId
	 *            the linuxNormalVmImageId to set
	 */
	public void setLinuxNormalVmImageId(String linuxNormalVmImageId) {
		this.linuxNormalVmImageId = linuxNormalVmImageId;
	}

	/**
	 * @return the linuxHelperVmImageId
	 */
	public String getLinuxHelperVmImageId() {
		return linuxHelperVmImageId;
	}

	/**
	 * @param linuxHelperVmImageId
	 *            the linuxHelperVmImageId to set
	 */
	public void setLinuxHelperVmImageId(String linuxHelperVmImageId) {
		this.linuxHelperVmImageId = linuxHelperVmImageId;
	}

	/**
	 * @return the androidNormalVmImageId
	 */
	public String getAndroidNormalVmImageId() {
		return androidNormalVmImageId;
	}

	/**
	 * @param androidNormalVmImageId
	 *            the androidNormalVmImageId to set
	 */
	public void setAndroidNormalVmImageId(String androidNormalVmImageId) {
		this.androidNormalVmImageId = androidNormalVmImageId;
	}

	/**
	 * @return the androidHelperVmImageId
	 */
	public String getAndroidHelperVmImageId() {
		return androidHelperVmImageId;
	}

	/**
	 * @param androidHelperVmImageId
	 *            the androidHelperVmImageId to set
	 */
	public void setAndroidHelperVmImageId(String androidHelperVmImageId) {
		this.androidHelperVmImageId = androidHelperVmImageId;
	}

	/**
	 * @return the authAddress
	 */
	public String getAuthAddress() {
		return authAddress;
	}

	/**
	 * @param authAddress
	 *            the authAddress to set
	 */
	public void setAuthAddress(String authAddress) {
		this.authAddress = authAddress;
	}

	/**
	 * @return the openStackUserId
	 */
	public String getOpenStackUserId() {
		return openStackUserId;
	}

	/**
	 * @param openStackUserId
	 *            the openStackUserId to set
	 */
	public void setOpenStackUserId(String openStackUserId) {
		this.openStackUserId = openStackUserId;
	}

	/**
	 * @return the openStackPasswd
	 */
	public String getOpenStackPasswd() {
		return openStackPasswd;
	}

	/**
	 * @param openStackPasswd
	 *            the openStackPasswd to set
	 */
	public void setOpenStackPasswd(String openStackPasswd) {
		this.openStackPasswd = openStackPasswd;
	}

	/**
	 * @return the projectId1
	 */
	public String getProjectId1() {
		return projectId1;
	}

	/**
	 * @param projectId1
	 *            the projectId1 to set
	 */
	public void setProjectId1(String projectId1) {
		this.projectId1 = projectId1;
	}

	/**
	 * @return the projectId2
	 */
	public String getProjectId2() {
		return projectId2;
	}

	/**
	 * @param projectId2
	 *            the projectId2 to set
	 */
	public void setProjectId2(String projectId2) {
		this.projectId2 = projectId2;
	}

	/**
	 * @return the networkId
	 */
	public String getNetworkId() {
		return networkId;
	}

	/**
	 * @param networkId
	 *            the networkId to set
	 */
	public void setNetworkId(String networkId) {
		this.networkId = networkId;
	}

	/**
	 * @return the publicKey
	 */
	public String getPublicKey() {
		return publicKey;
	}

	/**
	 * @param publicKey
	 *            the publicKey to set
	 */
	public void setPublicKey(String publicKey) {
		this.publicKey = publicKey;
	}

	/**
	 * @return the offloadingSecurity
	 */
	public String getOffloadingSecurity() {
		return offloadingSecurity;
	}

	/**
	 * @param offloadingSecurity
	 *            the offloadingSecurity to set
	 */
	public void setOffloadingSecurity(String offloadingSecurity) {
		this.offloadingSecurity = offloadingSecurity;
	}

	/**
	 * @return
	 */
	public String getIpShellPath() {
		return ipShellPath;
	}

	/**
	 * @param ipShellPath
	 */
	public void setIpShellPath(String ipShellPath) {
		this.ipShellPath = ipShellPath;
	}

	public OSClient.OSClientV3 getOs() {
		return os;
	}

	public void setOs(OSClient.OSClientV3 os) {
		this.os = os;
	}

	/**
	 * @return
	 */
	public String getVmShellPath() {
		return vmShellPath;
	}

	/**
	 * @param vmShellPath
	 */
	public void setVmShellPath(String vmShellPath) {
		this.vmShellPath = vmShellPath;
	}

	/**
	 * @return
	 */
	public List<VmInfo> getVmList() {
		return vmList;
	}

	/**
	 * @param vmList
	 */
	public void setVmList(List<VmInfo> vmList) {
		this.vmList = vmList;
	}
}
