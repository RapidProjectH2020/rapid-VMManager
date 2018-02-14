package eu.rapid.vmm;

import java.io.Serializable;

public class VmInfo implements Serializable {
	/**
	 * 
	 */

	static final int NORMAL_VM = 0;
	static final int HELPER_VM = 1;

	static final int HELPER_VM_USER_ID = 0;

	static final int VM_STARTED = 1;
	static final int VM_SUSPENDED = 2;
	static final int VM_RESUMED = 3;
	static final int VM_STOPPED = 4;

	private static final long serialVersionUID = -7722698437532950775L;
	private String vmName;
	private String vmIdByLM;
	private long vmid; // vmid by DS
	private int vcpuNum;
	private int memSize;
	private int gpuCore;
	private int diskSize;
	private long userid;
	private int status;
	private int helperVmNum;
	private String vmImagePath;
	private String vmImageId;
	private String ipAddress;
	private boolean asSync;
	private int osType;
	private org.openstack4j.model.compute.Flavor flavor;

	/**
	 * @return
	 */
	public String getVmName() {
		return vmName;
	}

	/**
	 * @param vmName
	 */
	public void setVmName(String vmName) {
		this.vmName = vmName;
	}

	/**
	 * @return
	 */
	public String getVmIdByLM() {
		return vmIdByLM;
	}

	/**
	 * @param vmIdByLM
	 */
	public void setVmIdByLM(String vmIdByLM) {
		this.vmIdByLM = vmIdByLM;
	}

	/**
	 * @return
	 */
	public long getVmid() {
		return vmid;
	}

	/**
	 * @param vmid
	 */
	public void setVmid(long vmid) {
		this.vmid = vmid;
	}

	public int getVcpuNum() {
		return vcpuNum;
	}

	public void setVcpuNum(int vcpuNum) {
		this.vcpuNum = vcpuNum;
	}

	public int getMemSize() {
		return memSize;
	}

	public void setMemSize(int memSize) {
		this.memSize = memSize;
	}

	public int getGpuCore() {
		return gpuCore;
	}

	public void setGpuCore(int gpuCore) {
		this.gpuCore = gpuCore;
	}

	public long getDiskSize() {
		return diskSize;
	}

	public void setDiskSize(int diskSize) {
		this.diskSize = diskSize;
	}

	/**
	 * @return
	 */
	public long getUserid() {
		return userid;
	}

	/**
	 * @param userId
	 */
	public void setUserid(long userId) {
		this.userid = userId;
	}

	/**
	 * @return
	 */
	public int getStatus() {
		return status;
	}

	/**
	 * @param status
	 */
	public void setStatus(int status) {
		this.status = status;
	}

	/**
	 * @return
	 */
	public int getHelperVmNum() {
		return helperVmNum;
	}

	/**
	 * @param helperVmNum
	 */
	public void setHelperVmNum(int helperVmNum) {
		this.helperVmNum = helperVmNum;
	}

	/**
	 * @return
	 */
	public String getVmImagePath() {
		return vmImagePath;
	}

	/**
	 * @param vmImagePath
	 */
	public void setVmImagePath(String vmImagePath) {
		this.vmImagePath = vmImagePath;
	}

	/**
	 * @return the vmImageId
	 */
	public String getVmImageId() {
		return vmImageId;
	}

	/**
	 * @param vmImageId
	 *            the vmImageId to set
	 */
	public void setVmImageId(String vmImageId) {
		this.vmImageId = vmImageId;
	}

	/**
	 * @return the ipAddress
	 */
	public String getIpAddress() {
		return ipAddress;
	}

	/**
	 * @param ipAddress
	 *            the ipAddress to set
	 */
	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}

	/**
	 * @return the asSync
	 */
	public synchronized boolean isAsSync() {
		return asSync;
	}

	/**
	 * @param asSync
	 *            the asSync to set
	 */
	public synchronized void setAsSync(boolean asSync) {
		this.asSync = asSync;
	}

	/**
	 * @return the osType
	 */
	public int getOsType() {
		return osType;
	}

	/**
	 * @param osType
	 *            the osType to set
	 */
	public void setOsType(int osType) {
		this.osType = osType;
	}

	public org.openstack4j.model.compute.Flavor getFlavor() {
		return flavor;
	}

	public void setFlavor(org.openstack4j.model.compute.Flavor flavor) {
		this.flavor = flavor;
	}
}
