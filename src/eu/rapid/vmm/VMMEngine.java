package eu.rapid.vmm;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.openstack4j.api.OSClient;
import org.openstack4j.api.compute.FlavorService;
import org.openstack4j.model.compute.Action;
import org.openstack4j.model.compute.Server;
import org.openstack4j.model.telemetry.Meter;
import org.openstack4j.model.telemetry.Sample;
import org.openstack4j.model.telemetry.SampleCriteria;
import org.openstack4j.model.telemetry.Statistics;

import eu.project.rapid.common.RapidConstants;
import eu.project.rapid.common.RapidMessages;
import eu.project.rapid.common.RapidUtils;
import eu.rapid.vmmanager.api.RapidVMManager;
import eu.rapid.vmmanager.api.impl.RapidVMManagerImpl;

public class VMMEngine {
	private static VMMEngine vmmEngine = new VMMEngine();
	private Logger logger = Logger.getLogger(getClass());

	static final int macType = 4;
	static final int maxAvailableType = 18;
	static final int cpuType = 0;
	static final int memType = 1;
	static final int diskType = 2;
	static final int asPort = 4322;
	private int gpuCores;
	private String availableType;
	private int[] availableTypeArray;
	private VmType[] vmTypes;

	static final int numCpuType = 3;
	static final int numMemType = 3;
	static final int numDiskType = 2;
	private int[] cpuTypeValues = new int[VMMEngine.numCpuType];
	private int[] memTypeValues = new int[VMMEngine.numMemType];
	private int[] diskTypeValues = new int[VMMEngine.numDiskType];

	private VmmInfo vmmInfo;

	private VMMEngine() {
		vmTypes = new VmType[VMMEngine.maxAvailableType];

		initTypeValues();
	}

	public static VMMEngine getInstance() {
		return vmmEngine;
	}

	private void initTypeValues() {
		cpuTypeValues[0] = 1;
		cpuTypeValues[1] = 2;
		cpuTypeValues[2] = 4;

		memTypeValues[0] = 1024;
		memTypeValues[1] = 2048;
		memTypeValues[2] = 4096;

		diskTypeValues[0] = 20;
		diskTypeValues[1] = 40;
	}

	public VmType findSuitableFlavorId(int vcpuNum, int memSize, int diskSize) {

		int targetVcpuNum = cpuTypeValues[0];
		int targetMemSize = memTypeValues[0];
		int targetDiskSize = diskTypeValues[0];

		// cpu check
		if (vcpuNum >= cpuTypeValues[numCpuType - 1]) {
			targetVcpuNum = cpuTypeValues[numCpuType - 1];
		} else {
			for (int i = 0; i < numCpuType - 1; i++) {
				if (vcpuNum >= cpuTypeValues[i] && vcpuNum < cpuTypeValues[i + 1]) {
					targetVcpuNum = cpuTypeValues[i];
					break;
				}
			}
		}

		// memory check
		if (memSize >= memTypeValues[numMemType - 1]) {
			targetMemSize = memTypeValues[numMemType - 1];
		} else {
			for (int i = 0; i < numMemType - 1; i++) {
				if (memSize >= memTypeValues[i] && memSize < memTypeValues[i + 1]) {
					targetMemSize = memTypeValues[i];
					break;
				}
			}
		}

		// disk check
		if (diskSize >= diskTypeValues[numDiskType - 1]) {
			targetDiskSize = diskTypeValues[numDiskType - 1];
		} else {
			for (int i = 0; i < numDiskType - 1; i++) {
				if (diskSize >= diskTypeValues[i] && diskSize < diskTypeValues[i + 1]) {
					targetDiskSize = diskTypeValues[i];
					break;
				}
			}
		}

		for (int i = 0; i < maxAvailableType; i++) {
			VmType vmType = vmTypes[i];
			if (vmType.getNumCore() == targetVcpuNum && vmType.getMemory() == targetMemSize
					&& vmType.getDisk() == targetDiskSize) {
				return vmType;
			}
		}

		return vmTypes[0];
	}

	private void helperVmRegisterDs(VmInfo vmInfo) {
		String ipAddress = vmInfo.getIpAddress();

		try {
			// send the IP address of this helper VM to the DS
			if (ipAddress != null && !ipAddress.equals("null")) {
				VMManager vmManager = VMManager.getInstance();
				Socket vmmDsSocket = new Socket(vmManager.getDsAddress(), vmManager.getDSPort());
				ObjectOutputStream dsOut = new ObjectOutputStream(vmmDsSocket.getOutputStream());
				dsOut.flush();
				ObjectInputStream dsIn = new ObjectInputStream(vmmDsSocket.getInputStream());

				dsOut.writeByte(RapidMessages.HELPER_NOTIFY_DS);

				dsOut.writeLong(vmInfo.getVmid()); // vmid
				logger.info("helperVM ip addres: " + ipAddress);
				dsOut.writeUTF(ipAddress); // ipAddress
				dsOut.flush();

				dsOut.close();
				dsIn.close();
				vmmDsSocket.close();
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

	@SuppressWarnings("resource")
	private void launchAHelperVm(long userid) {
		LifetimeManager lifetimeManager = LifetimeManager.getInstance();
		VMManager vmManager = VMManager.getInstance();

		try {
			int helperVmType = 0;

			Socket vmmDsSocket = new Socket(vmManager.getDsAddress(), vmManager.getDSPort());
			ObjectOutputStream dsOut = new ObjectOutputStream(vmmDsSocket.getOutputStream());
			dsOut.flush();
			ObjectInputStream dsIn = new ObjectInputStream(vmmDsSocket.getInputStream());

			VmInfo vmInfo = null;
			String flavorId;
			int vcpuNum;

			if (!vmManager.isOpenStack())
				vmInfo = lifetimeManager.createVM(userid, vmTypes[helperVmType].getMemory(),
						vmTypes[helperVmType].getNumCore(), vmTypes[helperVmType].getDisk(), (int) -userid);
			else {

				flavorId = lifetimeManager.getType4_1024_20FlavorId();
				vcpuNum = 4;

				vmInfo = lifetimeManager.createVM(userid, flavorId, (int) -userid,
						RapidConstants.OS.valueOf("ANDROID").ordinal());

				if (vmInfo != null) {
					vmInfo.setVcpuNum(vcpuNum);
					vmInfo.setMemSize(1024);
					vmInfo.setGpuCore(0);
				}

			}

			if (vmInfo == null)
				return;

			dsOut.writeByte(RapidMessages.VM_REGISTER_DS);

			dsOut.writeLong(vmmInfo.getVmmId()); // vmmid
			dsOut.writeInt(VmInfo.HELPER_VM); // category
			dsOut.writeInt(helperVmType); // type
			dsOut.writeLong(userid); // userid
			dsOut.writeInt(VmInfo.VM_STARTED); // vmstatus
			dsOut.flush();

			long vmid = dsIn.readLong(); // vmid
			vmInfo.setVmid(vmid);
			lifetimeManager.saveVmList();

			dsOut.close();
			dsIn.close();
			vmmDsSocket.close();

			helperVmRegisterDs(vmInfo);

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
	 * This function starts two helper VMs in the initialization process.
	 */
	public void startHelperVms() {
		LifetimeManager lifetimeManager = LifetimeManager.getInstance();

		/* create two helper VMs */
		List<VmInfo> vmList = lifetimeManager.getVmList();
		int helperVm1 = 0;
		int helperVm2 = 0;

		Iterator<VmInfo> iterator = vmList.iterator();
		while (iterator.hasNext()) {
			VmInfo vmInfo = (VmInfo) iterator.next();
			if (vmInfo.getHelperVmNum() == 1)
				helperVm1 = 1;
			if (vmInfo.getHelperVmNum() == 2)
				helperVm2 = 1;
		}

		// the user ID of the first helper VM is -1, and the second is -2.
		if (helperVm1 == 1 && helperVm2 == 1) {
			return;
		} else if (helperVm1 == 0 && helperVm2 == 1) {
			launchAHelperVm(-1);
		} else if (helperVm1 == 1 && helperVm2 == 0) {
			launchAHelperVm(-2);
		} else {
			launchAHelperVm(-1);
			launchAHelperVm(-2);
		}
	}

	private boolean waitForASRmRegister(VmInfo vmInfo) {
		// wait for the AS to register for 200 seconds.

		int sleepCount = 0;
		do {
			sleepCount++;

			try {
				Thread.sleep(1 * 1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			if (vmInfo.isAsSync()) {
				return true;
			}

		} while (sleepCount <= 200);

		return false;
	}

	/**
	 * The function deals with the SLAM_START_VM_VMM message. It creates or
	 * resumes a VM.
	 * 
	 * @param in
	 *            ObjectInputStream instance retrieved by the socket.
	 * @param out
	 *            ObjectOutputStream instance retrieved by the socket.
	 */
	public void slamStartVmVMM(ObjectInputStream in, ObjectOutputStream out) {
		LifetimeManager lifetimeManager = LifetimeManager.getInstance();
		VMManager vmManager = VMManager.getInstance();

		try {
			long userid = in.readLong();
			int osType = in.readInt();
			int vcpuNum = in.readInt();
			int memSize = in.readInt();
			int gpuCores = in.readInt();
			int diskSize;

			// System.out.println("in slamStartVmVMM, userId: " + userid + "
			// osType: " + osType + " vcpuNum: " + vcpuNum + " memSize: " +
			// memSize + " gpuCores: " + gpuCores);
			logger.info("SLAM_START_VM_VMM, userId: " + userid + " osType: " + osType + " vcpuNum: " + vcpuNum
					+ " memSize: " + memSize + " gpuCores: " + gpuCores);

			/*
			 * if (availableTypeArray[0] == 0) {
			 * out.writeByte(RapidMessages.ERROR); out.flush(); return; }
			 */

			VmInfo vmInfo = lifetimeManager.getVmInfoByUserId(userid);

			if (vmInfo != null
					&& (vmInfo.getStatus() == VmInfo.VM_STARTED || vmInfo.getStatus() == VmInfo.VM_RESUMED)) {
				// Find a previous VM
				// RapidUtils.sendAnimationMsg(vmManager.getAnimationAddress(),
				// vmManager.getAnimationPort(),
				// RapidMessages.AnimationMsg.SLAM_PREV_VM_REQ_VMM.toString());
				// RapidUtils.sendAnimationMsg(vmManager.getAnimationAddress(),
				// vmManager.getAnimationPort(),
				// RapidMessages.AnimationMsg.VMM_PREV_FIND_VM.toString());
				logger.info("Find the previous VM, userId: " + vmInfo.getUserid());

				logger.info("Send IP address to the SLAM: " + vmInfo.getIpAddress() + " for user id : "
						+ vmInfo.getUserid());
				out.writeByte(RapidMessages.OK);
				out.writeLong(vmInfo.getUserid());
				out.writeUTF(vmInfo.getIpAddress());
				out.flush();

				// OK <vm ip>
				// RapidUtils.sendAnimationMsg(vmManager.getAnimationAddress(),
				// vmManager.getAnimationPort(),
				// RapidMessages.AnimationMsg.VMM_PREV_VM_IP_SLAM.toString());
				// RapidUtils.sendAnimationMsg(vmManager.getAnimationAddress(),
				// vmManager.getAnimationPort(),
				// RapidMessages.AnimationMsg.SLAM_PREV_VM_IP_AC.toString());
				return;
			}

			// create a VM.
			if (vmInfo == null) {

				if (!vmManager.isOpenStack()) {
					vmInfo = lifetimeManager.createVM(userid, memSize * 1024, vcpuNum, vmTypes[0].getDisk(), 0);
				} else {
					// find a suitable Flavor.
					VmType vmType = findSuitableFlavorId(vcpuNum, memSize, 20);
					logger.info("Requested resources. vcpuNum: " + vcpuNum + " memSize: " + memSize + " diskSize: 20");
					logger.info("Selected Flavor ID: " + vmType.getFlavorId());

					/*
					 * String flavorId = lifetimeManager.getDefaultFlavorId();
					 * if (vcpuNum <= 0) { flavorId =
					 * lifetimeManager.getDefaultFlavorId(); vcpuNum = 1; }
					 * 
					 * if (vcpuNum > 4) { flavorId =
					 * lifetimeManager.getType4_1024_20FlavorId(); vcpuNum = 4;
					 * }
					 * 
					 * switch (vcpuNum) { case 1: flavorId =
					 * lifetimeManager.getDefaultFlavorId(); vcpuNum = 1; break;
					 * case 2: flavorId =
					 * lifetimeManager.getType2_1024_20FlavorId(); vcpuNum = 2;
					 * break; case 3: flavorId =
					 * lifetimeManager.getType4_1024_20FlavorId(); vcpuNum = 4;
					 * break; case 4: flavorId =
					 * lifetimeManager.getType4_1024_20FlavorId(); vcpuNum = 4;
					 * break; }
					 */

					vmInfo = lifetimeManager.createVM(userid, vmType.getFlavorId(), 0, osType);

					if (vmInfo != null) {
						vmInfo.setVcpuNum(vmType.getNumCore());
						vmInfo.setMemSize((int)vmType.getMemory());
						vmInfo.setDiskSize(20);
						vmInfo.setGpuCore(gpuCores);
						lifetimeManager.saveVmList();
					}
				}

				if (vmInfo == null) {
					out.writeByte(RapidMessages.ERROR);
					out.flush();
					return;
				}

				logger.info("Create a new VM, userId: " + userid);

				// Create a VM
				// RapidUtils.sendAnimationMsg(vmManager.getAnimationAddress(),
				// vmManager.getAnimationPort(),
				// RapidMessages.AnimationMsg.SLAM_NEW_VM_VMM.toString());
				// RapidUtils.sendAnimationMsg(vmManager.getAnimationAddress(),
				// vmManager.getAnimationPort(),
				// RapidMessages.AnimationMsg.VMM_NEW_START_VM.toString());

				// for synchronizing with AS
				vmInfo.setAsSync(false);

				if (vmInfo.getIpAddress() == null || vmInfo.getIpAddress().equals("null")
						|| vmInfo.getIpAddress().equals("")) {
					lifetimeManager.removeVM(userid);
					out.writeByte(RapidMessages.ERROR);
					out.flush();
					return;
				}

				if (vmInfo.getVmIdByLM() != null && !vmInfo.getVmIdByLM().isEmpty()) {
					Socket vmmDsSocket = new Socket(vmManager.getDsAddress(), vmManager.getDSPort());
					ObjectOutputStream dsOut = new ObjectOutputStream(vmmDsSocket.getOutputStream());
					dsOut.flush();
					ObjectInputStream dsIn = new ObjectInputStream(vmmDsSocket.getInputStream());

					dsOut.writeByte(RapidMessages.VM_REGISTER_DS);

					dsOut.writeLong(vmmInfo.getVmmId()); // vmmid
					dsOut.writeInt(VmInfo.NORMAL_VM); // category
					dsOut.writeInt(0); // vmType
					dsOut.writeLong(userid); // userid
					dsOut.writeInt(VmInfo.VM_STARTED); // vmstatus
					dsOut.flush();

					long vmid = dsIn.readLong(); // vmid
					vmInfo.setVmid(vmid);
					lifetimeManager.saveVmList();

					dsOut.close();
					dsIn.close();
					vmmDsSocket.close();

					if (waitForASRmRegister(vmInfo)) {
						logger.info("Send IP address to the SLAM: " + vmInfo.getIpAddress() + " for user id : "
								+ vmInfo.getUserid());
						out.writeByte(RapidMessages.OK);
						out.writeLong(vmInfo.getUserid());
						out.writeUTF(vmInfo.getIpAddress());
						out.flush();

						// OK <vm ip>
						// RapidUtils.sendAnimationMsg(vmManager.getAnimationAddress(),
						// vmManager.getAnimationPort(),
						// RapidMessages.AnimationMsg.VMM_NEW_VM_IP_SLAM.toString());
						// RapidUtils.sendAnimationMsg(vmManager.getAnimationAddress(),
						// vmManager.getAnimationPort(),
						// RapidMessages.AnimationMsg.SLAM_NEW_VM_IP_AC.toString());

					} else {
						logger.info("Failed to send IP address to the SLAM");
						out.writeByte(RapidMessages.ERROR);
						out.flush();
						return;
					}
				} else {
					out.writeByte(RapidMessages.ERROR);
					out.flush();
					return;
				}
			}

			// resume a VM.
			if (vmInfo != null && vmInfo.getStatus() == VmInfo.VM_SUSPENDED) {
				// for synchronizing with AS
				vmInfo.setAsSync(false);

				boolean success = lifetimeManager.resumeVM(userid);

				if (success == true) {
					Socket vmmDsSocket = new Socket(vmManager.getDsAddress(), vmManager.getDSPort());
					ObjectOutputStream dsOut = new ObjectOutputStream(vmmDsSocket.getOutputStream());
					dsOut.flush();
					ObjectInputStream dsIn = new ObjectInputStream(vmmDsSocket.getInputStream());

					dsOut.writeByte(RapidMessages.VM_NOTIFY_DS);

					dsOut.writeLong(vmInfo.getVmid());
					dsOut.writeInt(VmInfo.VM_RESUMED); // vmstatus
					dsOut.flush();

					dsOut.close();
					dsIn.close();
					vmmDsSocket.close();

					if (waitForASRmRegister(vmInfo)) {
						logger.info("Send IP address to the SLAM: " + vmInfo.getIpAddress() + " for user id : "
								+ vmInfo.getUserid());
						out.writeByte(RapidMessages.OK);
						out.writeLong(vmInfo.getUserid());
						out.writeUTF(vmInfo.getIpAddress());
						out.flush();
					} else {
						logger.info("Failed to send IP address to the SLAM");
						out.writeByte(RapidMessages.ERROR);
						out.flush();
						return;
					}
				} else {
					out.writeByte(RapidMessages.ERROR);
					out.flush();
					return;
				}
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
	 * The function deals with the VMM_REGISTER_DS message. It registers the VMM
	 * to the DS.
	 * 
	 * @return success status. If the output is 0, the VMM registration is
	 *         successful.
	 */
	public int vmmRegisterDs() {
		ResourceMonitor resourceMonitor = ResourceMonitor.getInstance();
		VMManager vmManager = VMManager.getInstance();

		int errorCode = RapidMessages.ERROR;

		try {
			Socket vmmDsSocket = new Socket(vmManager.getDsAddress(), vmManager.getDSPort());
			ObjectOutputStream dsOut = new ObjectOutputStream(vmmDsSocket.getOutputStream());
			dsOut.flush();
			ObjectInputStream dsIn = new ObjectInputStream(vmmDsSocket.getInputStream());

			dsOut.writeByte(RapidMessages.VMM_REGISTER_DS);

			dsOut.writeUTF(vmManager.getVmmAddress());
			dsOut.writeInt(macType);
			dsOut.writeInt((int) resourceMonitor.getSystemCpuLoad());
			dsOut.writeInt(resourceMonitor.getNumberOfCPUCores());
			dsOut.writeLong(resourceMonitor.getFreePhysicalMemorySize());
			dsOut.writeInt(resourceMonitor.getSystemGpuLoad());
			dsOut.writeInt(gpuCores);
			dsOut.writeUTF(availableType);
			dsOut.flush();

			errorCode = dsIn.readByte();

			if (errorCode == RapidMessages.OK) {
				long vmmId = dsIn.readLong();
				vmmInfo.setVmmId(vmmId);

				SlamInfo slamInfo = new SlamInfo();
				slamInfo.setIpv4(dsIn.readUTF());
				slamInfo.setPort(dsIn.readInt());
				vmmInfo.setSlamInfo(slamInfo);

				saveVmmInfo();
			}

			dsOut.close();
			dsIn.close();
			vmmDsSocket.close();
		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
			e.printStackTrace();
		}

		return errorCode;
	}

	/**
	 * The function deals with the VMM_REGISTER_SLAM message. It registers the
	 * VMM to the SLAM.
	 * 
	 * @return success status.
	 */
	public int vmmRegisterSlam() {
		VMManager vmManager = VMManager.getInstance();

		int errorCode = RapidMessages.ERROR;

		try {

			logger.info(
					"SLAM IP: " + vmmInfo.getSlamInfo().getIpv4() + " SLAM port: " + vmmInfo.getSlamInfo().getPort());

			Socket vmmSlamSocket = new Socket(vmmInfo.getSlamInfo().getIpv4(), vmmInfo.getSlamInfo().getPort());
			ObjectOutputStream slamOut = new ObjectOutputStream(vmmSlamSocket.getOutputStream());
			slamOut.flush();
			ObjectInputStream slamIn = new ObjectInputStream(vmmSlamSocket.getInputStream());

			slamOut.writeByte(RapidMessages.VMM_REGISTER_SLAM);

			slamOut.writeUTF(vmManager.getVmmAddress());
			slamOut.writeInt(vmManager.getVMMPort());
			slamOut.flush();

			errorCode = slamIn.readByte();

			slamOut.close();
			slamIn.close();
			vmmSlamSocket.close();
		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
			e.printStackTrace();
		}

		return errorCode;
	}

	/**
	 * The function deals with the VMM_NOTIFY_DS message. It notifies the DS
	 * about free resource information.
	 */
	public void vmmNotifyDs() {
		VMManager vmManager = VMManager.getInstance();
		ResourceMonitor resourceMonitor = ResourceMonitor.getInstance();

		try {
			Socket vmmDsSocket = new Socket(vmManager.getDsAddress(), vmManager.getDSPort());
			ObjectOutputStream dsOut = new ObjectOutputStream(vmmDsSocket.getOutputStream());
			dsOut.flush();
			ObjectInputStream dsIn = new ObjectInputStream(vmmDsSocket.getInputStream());

			dsOut.writeByte(RapidMessages.VMM_NOTIFY_DS);

			dsOut.writeLong(vmmInfo.getVmmId());
			dsOut.writeInt((int) resourceMonitor.getSystemCpuLoad());
			dsOut.writeLong(resourceMonitor.getFreePhysicalMemorySize());
			dsOut.writeInt(resourceMonitor.getSystemGpuLoad());
			dsOut.flush();

			dsOut.close();
			dsIn.close();
			vmmDsSocket.close();
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
	 * The function deals with the AS_RM_NOTIFY_VMM message. It receives a
	 * message from the AS. According to the type contained in the message, it
	 * will suspend or remove a VM.
	 * 
	 * @param in
	 *            ObjectInputStream instance retrieved by the socket.
	 * @param out
	 *            ObjectOutputStream instance retrieved by the socket.
	 */
	public void asRmNotifyVmm(ObjectInputStream in, ObjectOutputStream out) {
		LifetimeManager lifetimeManager = LifetimeManager.getInstance();

		try {
			long userid = in.readLong();
			int type = in.readInt();

			boolean result = false;
			VmInfo vmInfo = lifetimeManager.getVmInfoByUserId(userid);

			if (type == 1) {
				result = lifetimeManager.suspendVM(userid);
			} else if (type == 2) {
				result = lifetimeManager.removeVM(userid);
			}

			if (result == true) {
				VMManager vmManager = VMManager.getInstance();
				Socket vmmDsSocket = new Socket(vmManager.getDsAddress(), vmManager.getDSPort());
				ObjectOutputStream dsOut = new ObjectOutputStream(vmmDsSocket.getOutputStream());
				dsOut.flush();
				ObjectInputStream dsIn = new ObjectInputStream(vmmDsSocket.getInputStream());

				dsOut.writeByte(RapidMessages.VM_NOTIFY_DS);

				dsOut.writeLong(vmInfo.getVmid());
				if (type == 1)
					dsOut.writeInt(VmInfo.VM_SUSPENDED); // vmstatus
				if (type == 2)
					dsOut.writeInt(VmInfo.VM_STOPPED); // vmstatus

				dsOut.flush();

				dsOut.close();
				dsIn.close();
				vmmDsSocket.close();
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
	 * The function deals with the DS_VM_DEREGISTER_VMM message. It receives the
	 * message from the DS and removes the VM associated the user Id in the
	 * message format.
	 * 
	 * @param in
	 *            ObjectInputStream instance retrieved by the socket.
	 * @param out
	 *            ObjectOutputStream instance retrieved by the socket.
	 */
	public void dsVmDeregisterVmm(ObjectInputStream in, ObjectOutputStream out) {
		LifetimeManager lifetimeManager = LifetimeManager.getInstance();

		try {
			long userid = in.readLong();

			lifetimeManager.removeVM(userid);
		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
			e.printStackTrace();
		}
	}

	private VmInfo getVmInfoByIpAddress(String ipAddress) {
		LifetimeManager lifetimeManager = LifetimeManager.getInstance();

		List<VmInfo> vmList = lifetimeManager.getVmList();
		VmInfo vmInfo = null;

		Iterator<VmInfo> iterator = vmList.iterator();
		while (iterator.hasNext()) {
			VmInfo tempVmInfo = (VmInfo) iterator.next();

			if (tempVmInfo.getIpAddress() != null && tempVmInfo.getIpAddress().equals(ipAddress)) {
				vmInfo = tempVmInfo;
				break;
			}
		}

		return vmInfo;
	}

	/**
	 * The function deals with the AS_RM_REGISTER_VMM message. It receives the
	 * message from the AS and returns the user id to the AS.
	 * 
	 * @param in
	 *            ObjectInputStream instance retrieved by the socket.
	 * @param out
	 *            ObjectOutputStream instance retrieved by the socket.
	 */
	public void asRmRegisterVmm(ObjectInputStream in, ObjectOutputStream out, Socket socket) {
		VMManager vmManager = VMManager.getInstance();

		try {
			RapidUtils.sendAnimationMsg(vmManager.getAnimationAddress(), vmManager.getAnimationPort(),
					RapidMessages.AnimationMsg.VMM_NEW_REGISTER_AS.toString());

			// retrieve the IP address of the AS
			String asIpAddress = in.readUTF();

			// find the target VM with asIpAddress
			VmInfo vmInfo = null;

			int sleepCount = 0;
			do {
				vmInfo = getVmInfoByIpAddress(asIpAddress);

				if (vmInfo != null)
					break;

				try {
					Thread.sleep(1 * 10000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				sleepCount++;
			} while (sleepCount <= 3);

			if (vmInfo == null) {
				logger.info("vmInfo is null. ipAddress from AS: " + asIpAddress);
				out.writeByte(RapidMessages.ERROR);
				out.flush();
				return;
			}

			out.writeByte(RapidMessages.OK);
			out.writeLong(vmInfo.getUserid());
			out.flush();

			byte status = in.readByte();

			RapidUtils.sendAnimationMsg(vmManager.getAnimationAddress(), vmManager.getAnimationPort(),
					RapidMessages.AnimationMsg.VMM_NEW_VM_REGISTER_DS.toString());

			if (status == RapidMessages.OK) {
				logger.info("status from AS: " + status);
				vmInfo.setAsSync(true);
			} else {
				logger.info("status from AS: " + status);
				vmInfo.setAsSync(false);
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
	 * The function deals with the SLAM_GET_VMCPU_VMM message. It receives the
	 * message from the SLAM and returns the CPU utilization of each VM to the
	 * SLAM.
	 * 
	 * @param in
	 *            ObjectInputStream instance retrieved by the socket.
	 * @param out
	 *            ObjectOutputStream instance retrieved by the socket.
	 */
	public void slamGetVmResourceVmm(ObjectInputStream in, ObjectOutputStream out, int resourceType) {
		LifetimeManager lifetimeManager = LifetimeManager.getInstance();

		try {
			long userid = in.readLong();

			logger.info("userid: " + userid);

			// openstack authentication
			OSClient.OSClientV3 os = lifetimeManager.openStackAuthenticate();

			if (os == null) {
				logger.info("os is null");
				out.writeByte(RapidMessages.ERROR);
				out.flush();
				return;
			}

			Map<String, Float> vmResourceMap = Collections.synchronizedMap(new Hashtable<String, Float>());
			Map<String, Float> vmResourceAuxMap = Collections.synchronizedMap(new Hashtable<String, Float>());

			List<? extends Sample> samples = null;
			List<? extends Sample> samplesAux = null;

			if (resourceType == cpuType) {
				samples = os.telemetry().meters().samples("cpu_util");
			}
			if (resourceType == memType) {
				samples = os.telemetry().meters().samples("memory");
				samplesAux = os.telemetry().meters().samples("memory.usage");
			}
			if (resourceType == diskType) {
				samples = os.telemetry().meters().samples("disk.capacity");
				samplesAux = os.telemetry().meters().samples("disk.allocation");
			}

			Iterator<? extends Sample> iterator = samples.iterator();
			while (iterator.hasNext()) {
				Sample sample = (Sample) iterator.next();

				synchronized (vmResourceMap) {
					vmResourceMap.put(sample.getResourceId(), sample.getCounterVolume());
				}
			}

			if (samplesAux != null) {
				Iterator<? extends Sample> iteratorAux = samplesAux.iterator();
				while (iteratorAux.hasNext()) {
					Sample sample = (Sample) iteratorAux.next();

					synchronized (vmResourceAuxMap) {
						vmResourceAuxMap.put(sample.getResourceId(), sample.getCounterVolume());
					}
				}
			}

			List<VmInfo> vmList = lifetimeManager.getVmList();
			ArrayList<String> vmResourceList = new ArrayList<String>();

			if (userid == 0) {
				Iterator<VmInfo> iter = vmList.iterator();

				while (iter.hasNext()) {
					VmInfo vmInfo = (VmInfo) iter.next();
					String value1 = null;
					String value2 = null;

					Iterator<String> keys = vmResourceMap.keySet().iterator();
					while (keys.hasNext()) {
						String key = keys.next();
						if (vmInfo.getVmIdByLM().equals(key)) {
							value1 = vmResourceMap.get(key).toString();
							break;
						}
					}

					Iterator<String> keysAux = vmResourceAuxMap.keySet().iterator();
					while (keysAux.hasNext()) {
						String key = keysAux.next();
						if (vmInfo.getVmIdByLM().equals(key)) {
							value2 = vmResourceAuxMap.get(key).toString();
							break;
						}
					}

					if ((resourceType == memType || resourceType == diskType) && value2 == null)
						continue;

					if (value1 != null) {
						vmResourceList.add(new Long(vmInfo.getUserid()).toString());
						vmResourceList.add(new Long(vmInfo.getVmid()).toString());
						if (resourceType == memType)
							vmResourceList.add(new Integer(vmInfo.getMemSize()).toString());
						else if (resourceType == diskType) {
							long diskSize = vmInfo.getDiskSize() * 1024 * 1024 * 1024;
							System.out.println("vmInfo.getDiskSize(): " + vmInfo.getDiskSize() + " diskSize: " + diskSize);
							vmResourceList.add(new Long(diskSize).toString());
						}
						else 
							vmResourceList.add(value1);
						if (value2 != null) {
							vmResourceList.add(value2);
						}
					}
				}
			} else {
				VmInfo vmInfo = (VmInfo) lifetimeManager.getVmInfoByUserId(userid);

				if (vmInfo == null) {
					logger.info("vmInfo is null");
					out.writeByte(RapidMessages.ERROR);
					out.flush();
					return;
				}

				String value1 = null;
				String value2 = null;

				Iterator<String> keys = vmResourceMap.keySet().iterator();
				while (keys.hasNext()) {
					String key = keys.next();
					if (vmInfo.getVmIdByLM().equals(key)) {
						value1 = vmResourceMap.get(key).toString();
						break;
					}
				}

				Iterator<String> keysAux = vmResourceAuxMap.keySet().iterator();
				while (keysAux.hasNext()) {
					String key = keysAux.next();
					if (vmInfo.getVmIdByLM().equals(key)) {
						value2 = vmResourceAuxMap.get(key).toString();
						break;
					}
				}
				
				if ((resourceType == memType || resourceType == diskType) && value2 == null) {
					logger.info("vmResourceList is null");
					out.writeByte(RapidMessages.ERROR);
					out.flush();
					return;
				}
					

				if (value1 != null) {
					vmResourceList.add(new Long(vmInfo.getUserid()).toString());
					vmResourceList.add(new Long(vmInfo.getVmid()).toString());
					if (resourceType == memType)
						vmResourceList.add(new Integer(vmInfo.getMemSize()).toString());
					else if (resourceType == diskType) {
						long diskSize = vmInfo.getDiskSize() * 1024L * 1024L * 1024L;
						System.out.println("vmInfo.getDiskSize(): " + vmInfo.getDiskSize() + " diskSize: " + diskSize);
						vmResourceList.add(new Long(diskSize).toString());
					}
					else 
						vmResourceList.add(value1);
					if (value2 != null)
						vmResourceList.add(value2);
				}
			}

			if (vmResourceList.size() == 0) {
				logger.info("vmResourceList is null");
				out.writeByte(RapidMessages.ERROR);
				out.flush();
				return;
			}

			out.writeByte(RapidMessages.OK);
			out.writeObject(vmResourceList);
			out.flush();

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
	 * The function deals with the SLAM_GET_VMINFO_VMM message. It receives the
	 * message from the SLAM and returns the information of each VM to the SLAM.
	 * 
	 * @param in
	 *            ObjectInputStream instance retrieved by the socket.
	 * @param out
	 *            ObjectOutputStream instance retrieved by the socket.
	 */
	public void slamGetVminfoVmm(ObjectInputStream in, ObjectOutputStream out) {
		LifetimeManager lifetimeManager = LifetimeManager.getInstance();

		try {

			long userid = in.readLong();

			ArrayList<String> vmInfoList = new ArrayList<String>();

			if (userid == 0) {

				List<VmInfo> vmList = lifetimeManager.getVmList();

				if (vmList.size() == 0) {
					out.writeByte(RapidMessages.ERROR);
					out.flush();
					return;
				}

				Iterator<VmInfo> iter = vmList.iterator();
				while (iter.hasNext()) {
					VmInfo vmInfo = (VmInfo) iter.next();

					if (vmInfo != null
							&& (vmInfo.getStatus() == VmInfo.VM_STARTED || vmInfo.getStatus() == VmInfo.VM_RESUMED)) {
						vmInfoList.add(new Long(vmInfo.getUserid()).toString());
						vmInfoList.add(new Long(vmInfo.getVmid()).toString());
						vmInfoList.add(new Integer(vmInfo.getVcpuNum()).toString());
						vmInfoList.add(new Integer(vmInfo.getMemSize()).toString());
						vmInfoList.add(new Long(vmInfo.getDiskSize()).toString());
						vmInfoList.add(new Integer(vmInfo.getGpuCore()).toString());
					}
				}
			} else {
				VmInfo vmInfo = (VmInfo) lifetimeManager.getVmInfoByUserId(userid);

				if (vmInfo != null
						&& (vmInfo.getStatus() == VmInfo.VM_STARTED || vmInfo.getStatus() == VmInfo.VM_RESUMED)) {
					vmInfoList.add(new Long(vmInfo.getUserid()).toString());
					vmInfoList.add(new Long(vmInfo.getVmid()).toString());
					vmInfoList.add(new Integer(vmInfo.getVcpuNum()).toString());
					vmInfoList.add(new Integer(vmInfo.getMemSize()).toString());
					vmInfoList.add(new Long(vmInfo.getDiskSize()).toString());
					vmInfoList.add(new Integer(vmInfo.getGpuCore()).toString());
				}
			}

			if (vmInfoList.size() == 0) {
				out.writeByte(RapidMessages.ERROR);
				out.flush();
				return;
			}

			out.writeByte(RapidMessages.OK);
			out.writeObject(vmInfoList);
			out.flush();

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
	 * The function deals with the SLAM_CHANGE_VMFLV_VMM message.
	 * 
	 * @param in
	 *            ObjectInputStream instance retrieved by the socket.
	 * @param out
	 *            ObjectOutputStream instance retrieved by the socket.
	 */
	public void slamChangeVmflvVmm(ObjectInputStream in, ObjectOutputStream out) {
		LifetimeManager lifetimeManager = LifetimeManager.getInstance();

		try {
			long userid = in.readLong();
			int vcpuNum = in.readInt();
			int memSize = in.readInt();
			int diskSize = in.readInt();

			// openstack authentication
			OSClient.OSClientV3 os = lifetimeManager.openStackAuthenticate();

			if (os == null) {
				out.writeByte(RapidMessages.ERROR);
				out.flush();
				return;
			}

			VmInfo vmInfo = lifetimeManager.getVmInfoByUserId(userid);
			if (vmInfo == null) {
				logger.info("in slamChangeVmflvVmm: error because vmInfo == null is null");

				out.writeByte(RapidMessages.ERROR);
				out.flush();
				return;
			}
			
			if (vcpuNum == 0) vcpuNum = vmInfo.getVcpuNum();
			if (memSize == 0) memSize = vmInfo.getMemSize();
			if (diskSize == 0) diskSize = (int) vmInfo.getDiskSize();
			
			Socket vmmAsSocket = new Socket(vmInfo.getIpAddress(), asPort);
			vmmAsSocket.setSoTimeout(10000);

			OutputStream asOut = vmmAsSocket.getOutputStream();
			asOut.flush();
			InputStream asIn = vmmAsSocket.getInputStream();

			ObjectOutputStream asObjectOut = new ObjectOutputStream(asOut);
			asObjectOut.flush();
			ObjectInputStream asObjectIn = new ObjectInputStream(asIn);

			asOut.write(RapidMessages.DS_MIGRATION_VM_AS);
			asOut.flush();

			asObjectOut.writeLong(vmInfo.getUserid()); // user_id
			asObjectOut.flush();

			logger.info("****** Trying to send a message to the AS ****");
			byte status = asObjectIn.readByte();
			logger.info("Return Status from AS: " + (status == RapidMessages.OK ? "OK" : "ERROR"));

			asObjectOut.close();
			asObjectIn.close();
			asOut.close();
			asIn.close();
			vmmAsSocket.close();

			if (status == RapidMessages.ERROR) {
				out.writeByte(RapidMessages.ERROR);
				out.flush();
				return;
			}

			// find a suitable Flavor.
			VmType vmType = findSuitableFlavorId(vcpuNum, memSize, diskSize);
			logger.info("Requested resources. vcpuNum: " + vcpuNum + " memSize: " + memSize + " diskSize: " + diskSize);
			logger.info("Selected Flavor ID: " + vmType.getFlavorId());
			/*
			 * String flavorId = lifetimeManager.getDefaultFlavorId(); if
			 * (vcpuNum <= 0) { flavorId = lifetimeManager.getDefaultFlavorId();
			 * vcpuNum = 1; }
			 * 
			 * if (vcpuNum > 4) { flavorId =
			 * lifetimeManager.getType4_1024_20FlavorId(); vcpuNum = 4; }
			 * 
			 * switch (vcpuNum) { case 1: flavorId =
			 * lifetimeManager.getDefaultFlavorId(); vcpuNum = 1; break; case 2:
			 * flavorId = lifetimeManager.getType2_1024_20FlavorId(); vcpuNum =
			 * 2; break; case 3: flavorId =
			 * lifetimeManager.getType4_1024_20FlavorId(); vcpuNum = 4; break;
			 * case 4: flavorId = lifetimeManager.getType4_1024_20FlavorId();
			 * vcpuNum = 4; break; }
			 */

			RapidVMManager t = new RapidVMManagerImpl();
			boolean result = t.resizeVM(os, vmInfo.getVmIdByLM(), vmType.getFlavorId());

			if (result == true) {
				vmInfo.setVcpuNum(vmType.getNumCore());
				vmInfo.setMemSize((int)vmType.getMemory());
				vmInfo.setDiskSize(vmType.getDisk());
	
				lifetimeManager.saveVmList();

				out.writeByte(RapidMessages.OK);
				out.flush();
				return;
			} else {
				logger.info("in slamChangeVmflvVmm: error because result is false");
				out.writeByte(RapidMessages.ERROR);
				out.flush();
				return;
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
	 * The function deals with the SLAM_CONFIRM_VMFLV_VMM message.
	 * 
	 * @param in
	 *            ObjectInputStream instance retrieved by the socket.
	 * @param out
	 *            ObjectOutputStream instance retrieved by the socket.
	 */
	public void slamConfirmVmflvVmm(ObjectInputStream in, ObjectOutputStream out) {
		LifetimeManager lifetimeManager = LifetimeManager.getInstance();

		try {
			long userid = in.readLong();

			VmInfo vmInfo = (VmInfo) lifetimeManager.getVmInfoByUserId(userid);

			if (vmInfo == null) {
				out.writeByte(RapidMessages.ERROR);
				out.flush();
				return;
			}

			// openstack authentication
			OSClient.OSClientV3 os = lifetimeManager.openStackAuthenticate();

			if (os == null) {
				out.writeByte(RapidMessages.ERROR);
				out.flush();
				return;
			}

			RapidVMManager t = new RapidVMManagerImpl();

			boolean result = t.ConfirmresizeVM(os, vmInfo.getVmIdByLM());

			if (result == true) {
				out.writeByte(RapidMessages.OK);
				out.flush();
				return;
			} else {
				out.writeByte(RapidMessages.ERROR);
				out.flush();
				return;
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
	 * This function initializes the VM type. Currently, VM type 0 is only
	 * supported.
	 */
	public void initializeVmTypes() {
		for (int i = 0; i < VMMEngine.maxAvailableType; i++)
			vmTypes[i] = new VmType();

		int count = 0;

		for (int i = 0; i < numCpuType; i++) {
			int cpuValue = cpuTypeValues[i];
			for (int j = 0; j < numMemType; j++) {
				int memValue = memTypeValues[j];
				for (int k = 0; k < numDiskType; k++) {
					int diskValue = diskTypeValues[k];

					vmTypes[count].setId(count);
					vmTypes[count].setNumCore(cpuValue);
					vmTypes[count].setMemory(memValue); // memory in MB
					vmTypes[count].setDisk(diskValue); // disk in GB
					vmTypes[count].setFlavorId(cpuValue + "_" + memValue + "_" + diskValue);

					if (vmTypes[count].getFlavorId().equals("1_1024_20"))
						vmTypes[count].setFlavorId("8ff03907-e210-4d5c-b328-54af88bc7733");

					if (vmTypes[count].getFlavorId().equals("2_1024_20"))
						vmTypes[count].setFlavorId("d97def8c-8917-47d0-97df-0cf508189ff1");

					if (vmTypes[count].getFlavorId().equals("2_2048_20"))
						vmTypes[count].setFlavorId("e2539be0-a220-40ff-b31c-2a51eb4304d3");

					if (vmTypes[count].getFlavorId().equals("4_1024_20"))
						vmTypes[count].setFlavorId("b8c86381-c303-4d2b-a665-a9cf5521e128");

					count++;
				}
			}
		}
	}

	/**
	 * This function restores the VMM information from the vmmInfo.out file.
	 */
	public synchronized void restoreVmmInfo() {
		VMManager vmManager = VMManager.getInstance();
		try {
			File file = new File(vmManager.getDataPath() + "/vmmInfo.out");
			if (file.exists() && !file.isDirectory()) {
				FileInputStream fis = new FileInputStream(vmManager.getDataPath() + "/vmmInfo.out");
				ObjectInputStream ois = new ObjectInputStream(fis);
				vmmInfo = (VmmInfo) ois.readObject();
				fis.close();
			} else {
				vmmInfo = new VmmInfo();
				if (!vmManager.isOpenStack())
					vmmInfo.setVmImageFreeList(Collections.synchronizedList(new ArrayList<String>()));
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
	 * This function saves the VMM information into the vmmInfo.out file for
	 * persistence.
	 */
	public synchronized void saveVmmInfo() {
		VMManager vmManager = VMManager.getInstance();
		try {
			FileOutputStream fos = new FileOutputStream(vmManager.getDataPath() + "/vmmInfo.out", false);
			ObjectOutputStream oos = new ObjectOutputStream(fos);

			oos.writeObject(vmmInfo);
			oos.flush();
			fos.close();
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
	 * @return
	 */
	public int getGpuCores() {
		return gpuCores;
	}

	/**
	 * @param gpuCores
	 */
	public void setGpuCores(int gpuCores) {
		this.gpuCores = gpuCores;
	}

	/**
	 * @return
	 */
	public String getAvailableType() {
		return availableType;
	}

	/**
	 * @param availableType
	 */
	public void setAvailableType(String availableType) {
		this.availableType = availableType;
	}

	/**
	 * @return
	 */
	public int[] getAvailableTypeArray() {
		return availableTypeArray;
	}

	/**
	 * @param availableTypeArray
	 */
	public void setAvailableTypeArray(int[] availableTypeArray) {
		this.availableTypeArray = availableTypeArray;
	}

	/**
	 * @return
	 */
	public VmType[] getVmTypes() {
		return vmTypes;
	}

	/**
	 * @param vmTypes
	 */
	public void setVmTypes(VmType[] vmTypes) {
		this.vmTypes = vmTypes;
	}

	/**
	 * @return
	 */
	public VmmInfo getVmmInfo() {
		return vmmInfo;
	}

	/**
	 * @param vmmInfo
	 */
	public void setVmmInfo(VmmInfo vmmInfo) {
		this.vmmInfo = vmmInfo;
	}
}
