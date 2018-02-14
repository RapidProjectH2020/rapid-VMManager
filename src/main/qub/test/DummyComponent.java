package main.qub.test;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;

import eu.project.rapid.common.RapidMessages;

public class DummyComponent {

	static final String dsAddress = "83.235.169.221";
	static final int dsPort = 9001;

	static final String vmmAddress = "83.235.169.221";
	static final int vmmPort = 9000;

	@SuppressWarnings({ "unchecked" })
	public static void main(String[] args) throws IOException {
		// TODO Auto-generated method stub
		System.out.println("DummyComponent Started");

		BufferedReader stdIn = new BufferedReader(new InputStreamReader(System.in));
		String fromClient;

		int command = 0;
		byte status = RapidMessages.ERROR;

		System.out.println("** Command List **");
		System.out.println("AC_REGISTER_NEW_DS: " + RapidMessages.AC_REGISTER_NEW_DS);
		System.out.println("AC_REGISTER_PREV_DS: " + RapidMessages.AC_REGISTER_PREV_DS);
		System.out.println("AS_RM_REGISTER_DS: " + RapidMessages.AS_RM_REGISTER_DS);
		System.out.println("AS_RM_REGISTER_VMM: " + RapidMessages.AS_RM_REGISTER_VMM);
		System.out.println("AS_RM_NOTIFY_VMM: " + RapidMessages.AS_RM_NOTIFY_VMM);
		System.out.println("FORWARD_REQ: " + RapidMessages.FORWARD_REQ);
		System.out.println("FORWARD_START: " + RapidMessages.FORWARD_START);
		System.out.println("FORWARD_END: " + RapidMessages.FORWARD_END);
		System.out.println("PARALLEL_REQ: " + RapidMessages.PARALLEL_REQ);
		System.out.println("PARALLEL_START: " + RapidMessages.PARALLEL_START);
		System.out.println("PARALLEL_END: " + RapidMessages.PARALLEL_END);
		System.out.println("SLAM_START_VM_VMM: " + RapidMessages.SLAM_START_VM_VMM);
		System.out.println("SLAM_REGISTER_DS: " + RapidMessages.SLAM_REGISTER_DS);
		System.out.println("VMM_REGISTER_SLAM: " + RapidMessages.VMM_REGISTER_SLAM);
		System.out.println("SLAM_GET_VMCPU_VMM: " + RapidMessages.SLAM_GET_VMCPU_VMM);
		System.out.println("SLAM_GET_VMINFO_VMM: " + RapidMessages.SLAM_GET_VMINFO_VMM);
		System.out.println("SLAM_CHANGE_VMFLV_VMM: " + RapidMessages.SLAM_CHANGE_VMFLV_VMM);
		System.out.println("SLAM_CONFIRM_VMFLV_VMM: " + RapidMessages.SLAM_CONFIRM_VMFLV_VMM);
		System.out.println("SLAM_GET_VMMEM_VMM: " + RapidMessages.SLAM_GET_VMMEM_VMM);
		System.out.println("SLAM_GET_VMDISK_VMM: " + RapidMessages.SLAM_GET_VMDISK_VMM);
		System.out.println("** Command List End **");
		System.out.println("");

		do {
			System.out.print("Please type Command as number: ");
			fromClient = stdIn.readLine();
			if (fromClient.equals(""))
				continue;

			try {
				command = Integer.parseInt(fromClient);
			} catch (Exception e) {
			}

			try {
				Socket vmmSocket;
				ObjectOutputStream vmmOut;
				ObjectInputStream vmmIn;

				Socket dsSocket;
				ObjectOutputStream dsOut;
				ObjectInputStream dsIn;

				switch (command) {
				case RapidMessages.SLAM_START_VM_VMM:
					System.out.println("SLAM_START_VM_VMM");
					vmmSocket = new Socket(vmmAddress, vmmPort);
					vmmOut = new ObjectOutputStream(vmmSocket.getOutputStream());
					vmmOut.flush();
					vmmIn = new ObjectInputStream(vmmSocket.getInputStream());

					// Send message format: command (java byte), userId (java
					// long), vmType (java int), osType (java int)

					vmmOut.writeByte(RapidMessages.SLAM_START_VM_VMM);
					System.out.print("Please type userId: ");
					fromClient = stdIn.readLine();
					vmmOut.writeLong(Long.parseLong(fromClient)); // userId
					System.out.print("Please type OS (0:Linux, 1:Andriod) : ");
					fromClient = stdIn.readLine();
					vmmOut.writeInt(Integer.parseInt(fromClient)); // os Type

					System.out.print("Please type vcpuNum: ");
					fromClient = stdIn.readLine();
					vmmOut.writeInt(Integer.parseInt(fromClient)); // userId
					System.out.print("Please type memSize in MB: ");
					fromClient = stdIn.readLine();
					vmmOut.writeInt(Integer.parseInt(fromClient)); // userId
					System.out.print(
							"Please type gpuCores (the number of required cores. if GPUs are not required, 0): ");
					fromClient = stdIn.readLine();
					vmmOut.writeInt(Integer.parseInt(fromClient)); // userId

					vmmOut.flush();

					// Receive message format: status (java byte), userId (java
					// long), ipAddress (java UTF)

					status = vmmIn.readByte();
					System.out.println("Return Status: " + (status == RapidMessages.OK ? "OK" : "ERROR"));
					if (status == RapidMessages.OK) {
						long userId = vmmIn.readLong();
						System.out.println("UserId is: " + userId);
						String ipAddress = vmmIn.readUTF();
						System.out.println("IP Address is: " + ipAddress);
					}

					vmmOut.close();
					vmmIn.close();
					vmmSocket.close();
					break;
				case RapidMessages.AS_RM_NOTIFY_VMM:
					System.out.println("AS_NOTIFY_VMM");
					vmmSocket = new Socket(vmmAddress, vmmPort);
					vmmOut = new ObjectOutputStream(vmmSocket.getOutputStream());
					vmmOut.flush();
					vmmIn = new ObjectInputStream(vmmSocket.getInputStream());

					// Send message format: command (java byte), userId (java
					// long), actionType (java int)

					vmmOut.writeByte(RapidMessages.AS_RM_NOTIFY_VMM);
					System.out.print("Please type userId: ");
					fromClient = stdIn.readLine();
					vmmOut.writeLong(Long.parseLong(fromClient)); // userId
					System.out.print("Please type action (1=suspend, 2=stop): ");
					fromClient = stdIn.readLine();
					command = Integer.parseInt(fromClient);
					vmmOut.writeInt(command); // if actionType == 1, suspend. if
												// actionType == 2, stop.
					vmmOut.flush();

					vmmOut.close();
					vmmIn.close();
					vmmSocket.close();
					break;
				case RapidMessages.AC_REGISTER_NEW_DS:
					System.out.println("AC_REGISTER_NEW_DS");
					dsSocket = new Socket(dsAddress, dsPort);
					dsOut = new ObjectOutputStream(dsSocket.getOutputStream());
					dsOut.flush();
					dsIn = new ObjectInputStream(dsSocket.getInputStream());

					// Send message format: command (java byte), userId (java
					// long), qosFlag (java int)

					dsOut.writeByte(RapidMessages.AC_REGISTER_NEW_DS);
					System.out.print("Please type userId (type -1 if this is first): ");
					fromClient = stdIn.readLine();
					dsOut.writeLong(Long.parseLong(fromClient)); // userId

					System.out.print("Please type vcpuNum: ");
					fromClient = stdIn.readLine();
					dsOut.writeInt(Integer.parseInt(fromClient)); // userId

					System.out.print("Please type memSize in MB: ");
					fromClient = stdIn.readLine();
					dsOut.writeInt(Integer.parseInt(fromClient)); // userId

					System.out.print(
							"Please type gpuCores (the number of required cores. if GPUs are not required, 0): ");
					fromClient = stdIn.readLine();
					dsOut.writeInt(Integer.parseInt(fromClient)); // userId

					dsOut.flush();

					// Receive message format: status (java byte), userId (java
					// long), ipList (java object), slampIp (java UTF), slamPort
					// (java int)

					status = dsIn.readByte();
					System.out.println("Return Status: " + (status == RapidMessages.OK ? "OK" : "ERROR"));
					if (status == RapidMessages.OK) {
						long userId = dsIn.readLong();
						System.out.println("New userId is: " + userId);
						ArrayList<String> ipList = (ArrayList<String>) dsIn.readObject();
						Iterator<String> ipListIterator = ipList.iterator();
						System.out.println("Received IP List: ");
						while (ipListIterator.hasNext())
							System.out.println(ipListIterator.next());
						String slamIpAddress = dsIn.readUTF();
						System.out.println("SLAM IP is: " + slamIpAddress);
						int slamPort = dsIn.readInt();
						System.out.println("SLAM port is: " + slamPort);
					}
					dsOut.close();
					dsIn.close();
					dsSocket.close();

					break;
				case RapidMessages.AC_REGISTER_PREV_DS:
					System.out.println("AC_REGISTER_PREV_DS");
					dsSocket = new Socket(dsAddress, dsPort);
					dsOut = new ObjectOutputStream(dsSocket.getOutputStream());
					dsOut.flush();
					dsIn = new ObjectInputStream(dsSocket.getInputStream());

					// Send message format: command (java byte), userId (java
					// long), qosFlag (java int)

					dsOut.writeByte(RapidMessages.AC_REGISTER_PREV_DS);
					System.out.print("Please type existing userId: ");
					fromClient = stdIn.readLine();
					dsOut.writeLong(Long.parseLong(fromClient)); // userId
					// dsOut.writeInt(0); // if there is QoS parameter, 1 else 0
					dsOut.flush();

					// Receive message format: status (java byte), userId (java
					// long), ipList (java object), slampIp (java UTF), slamPort
					// (java int)

					status = dsIn.readByte();
					System.out.println("Return Status: " + (status == RapidMessages.OK ? "OK" : "ERROR"));
					if (status == RapidMessages.OK) {
						long userId = dsIn.readLong();
						System.out.println("userId is: " + userId);
						ArrayList<String> ipList = (ArrayList<String>) dsIn.readObject();
						Iterator<String> ipListIterator = ipList.iterator();
						System.out.println("Received IP List: ");
						while (ipListIterator.hasNext())
							System.out.println(ipListIterator.next());
						String slamIpAddress = dsIn.readUTF();
						System.out.println("SLAM IP is: " + slamIpAddress);
						int slamPort = dsIn.readInt();
						System.out.println("SLAM port is: " + slamPort);
					}

					dsOut.close();
					dsIn.close();
					dsSocket.close();
					break;
				case RapidMessages.AS_RM_REGISTER_DS:
					System.out.println("AS_RM_REGISTER_DS");
					dsSocket = new Socket(dsAddress, dsPort);
					dsOut = new ObjectOutputStream(dsSocket.getOutputStream());
					dsOut.flush();
					dsIn = new ObjectInputStream(dsSocket.getInputStream());

					// Send message format: command (java byte), userId (java
					// long)

					dsOut.writeByte(RapidMessages.AS_RM_REGISTER_DS);
					System.out.print("Please type existing userId: ");
					fromClient = stdIn.readLine();
					dsOut.writeLong(Long.parseLong(fromClient)); // userId
					dsOut.flush();

					// Receive message format: status (java byte), vmId (java
					// long)

					status = dsIn.readByte();
					System.out.println("Return Status: " + (status == RapidMessages.OK ? "OK" : "ERROR"));
					if (status == RapidMessages.OK) {
						long vmId = dsIn.readLong();
						System.out.println("vmId is: " + vmId);
					}

					dsOut.close();
					dsIn.close();
					dsSocket.close();
					break;
				case RapidMessages.AS_RM_REGISTER_VMM:
					System.out.println("AS_RM_REGISTER_VMM");
					vmmSocket = new Socket(vmmAddress, vmmPort);
					vmmOut = new ObjectOutputStream(vmmSocket.getOutputStream());
					vmmOut.flush();
					vmmIn = new ObjectInputStream(vmmSocket.getInputStream());

					// Send message format: command (java byte), local ipAddress
					// (java UTF)

					String localIpAddress = "";
					Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
					while (e.hasMoreElements()) {
						NetworkInterface n = (NetworkInterface) e.nextElement();
						Enumeration<InetAddress> ee = n.getInetAddresses();

						boolean stop = false;
						while (ee.hasMoreElements()) {
							InetAddress i = (InetAddress) ee.nextElement();
							if (i.getHostAddress().startsWith("10.0.")) {
								localIpAddress = i.getHostAddress();
								stop = true;
								break;
							}
						}
						if (stop)
							break;
					}

					System.out.println("localIpAddress is: " + localIpAddress);

					vmmOut.writeByte(RapidMessages.AS_RM_REGISTER_VMM);
					vmmOut.writeUTF(localIpAddress);
					vmmOut.flush();

					// Receive message format: status (java byte), userId (java
					// long)
					status = vmmIn.readByte();
					System.out.println("Return Status: " + (status == RapidMessages.OK ? "OK" : "ERROR"));
					if (status == RapidMessages.OK) {
						long userId = vmmIn.readLong();
						System.out.println("userId is: " + userId);
					}

					// Send message format: status (java byte)
					if (status == RapidMessages.OK) {
						vmmOut.writeByte(RapidMessages.OK);
						vmmOut.flush();
					} else {
						vmmOut.writeByte(RapidMessages.ERROR);
						vmmOut.flush();
					}

					vmmOut.close();
					vmmIn.close();
					vmmSocket.close();
					break;
				case RapidMessages.FORWARD_REQ:
					System.out.println("FORWARD_REQ");
					dsSocket = new Socket(dsAddress, dsPort);
					dsOut = new ObjectOutputStream(dsSocket.getOutputStream());
					dsOut.flush();
					dsIn = new ObjectInputStream(dsSocket.getInputStream());

					// Send message format: command (java byte), vmId (java
					// long)

					dsOut.writeByte(RapidMessages.FORWARD_REQ);
					System.out.print("Please type existing vmId: ");
					fromClient = stdIn.readLine();
					dsOut.writeLong(Long.parseLong(fromClient)); // vmId
					dsOut.flush();

					// Receive message format: status (java byte), ipAddress
					// (java UTF)

					ArrayList<String> ipList = (ArrayList<String>) dsIn.readObject();
					Iterator<String> ipListIterator = ipList.iterator();
					System.out.println("Received IP List: ");
					while (ipListIterator.hasNext()) {
						System.out.println(ipListIterator.next());
					}

					dsOut.close();
					dsIn.close();
					dsSocket.close();
					break;
				case RapidMessages.FORWARD_START:
					System.out.println("FORWARD_START");
					dsSocket = new Socket(dsAddress, dsPort);
					dsOut = new ObjectOutputStream(dsSocket.getOutputStream());
					dsOut.flush();
					dsIn = new ObjectInputStream(dsSocket.getInputStream());

					// Send message format: command (java byte), vmId (java
					// long)

					dsOut.writeByte(RapidMessages.FORWARD_START);
					System.out.print("Please type existing vmId: ");
					fromClient = stdIn.readLine();
					dsOut.writeLong(Long.parseLong(fromClient)); // vmId
					dsOut.flush();

					dsOut.close();
					dsIn.close();
					dsSocket.close();
					break;
				case RapidMessages.FORWARD_END:
					System.out.println("FORWARD_END");
					dsSocket = new Socket(dsAddress, dsPort);
					dsOut = new ObjectOutputStream(dsSocket.getOutputStream());
					dsOut.flush();
					dsIn = new ObjectInputStream(dsSocket.getInputStream());

					// Send message format: command (java byte), vmId (java
					// long)

					dsOut.writeByte(RapidMessages.FORWARD_END);
					System.out.print("Please type existing vmId: ");
					fromClient = stdIn.readLine();
					dsOut.writeLong(Long.parseLong(fromClient)); // vmId
					dsOut.flush();

					dsOut.close();
					dsIn.close();
					dsSocket.close();
					break;
				case RapidMessages.PARALLEL_REQ:
					System.out.println("PARALLEL_REQ");
					dsSocket = new Socket(dsAddress, dsPort);
					dsOut = new ObjectOutputStream(dsSocket.getOutputStream());
					dsOut.flush();
					dsIn = new ObjectInputStream(dsSocket.getInputStream());

					// Send message format: command (java byte), vmId (java
					// long), helperVmNum (java int)

					dsOut.writeByte(RapidMessages.PARALLEL_REQ);
					System.out.print("Please type existing vmId: ");
					fromClient = stdIn.readLine();
					dsOut.writeLong(Long.parseLong(fromClient)); // vmId
					System.out.print("Please type number of helperVMs: ");
					fromClient = stdIn.readLine();
					dsOut.writeInt(Integer.parseInt(fromClient)); // number of
																	// helper
																	// VMs
					dsOut.flush();

					// Receive message format: status (java byte), ipList (java
					// object)

					ArrayList<String> ipList_p = (ArrayList<String>) dsIn.readObject();
					Iterator<String> ipListIterator_p = ipList_p.iterator();
					System.out.println("Received IP List: ");
					while (ipListIterator_p.hasNext()) {
						System.out.println(ipListIterator_p.next());
					}

					dsOut.close();
					dsIn.close();
					dsSocket.close();
					break;
				case RapidMessages.PARALLEL_START:
					System.out.println("PARALLEL_START");
					dsSocket = new Socket(dsAddress, dsPort);
					dsOut = new ObjectOutputStream(dsSocket.getOutputStream());
					dsOut.flush();
					dsIn = new ObjectInputStream(dsSocket.getInputStream());

					// Send message format: command (java byte), vmId (java
					// long)

					dsOut.writeByte(RapidMessages.PARALLEL_START);
					System.out.print("Please type existing vmId: ");
					fromClient = stdIn.readLine();
					dsOut.writeLong(Long.parseLong(fromClient)); // vmId
					dsOut.flush();

					dsOut.close();
					dsIn.close();
					dsSocket.close();
					break;
				case RapidMessages.PARALLEL_END:
					System.out.println("PARALLEL_END");
					dsSocket = new Socket(dsAddress, dsPort);
					dsOut = new ObjectOutputStream(dsSocket.getOutputStream());
					dsOut.flush();
					dsIn = new ObjectInputStream(dsSocket.getInputStream());

					// Send message format: command (java byte), vmId (java
					// long)

					dsOut.writeByte(RapidMessages.PARALLEL_END);
					System.out.print("Please type existing vmId: ");
					fromClient = stdIn.readLine();
					dsOut.writeLong(Long.parseLong(fromClient)); // vmId
					dsOut.flush();

					dsOut.close();
					dsIn.close();
					dsSocket.close();
					break;
				case RapidMessages.SLAM_REGISTER_DS:
					System.out.println("SLAM_REGISTER_DS");
					dsSocket = new Socket(dsAddress, dsPort);
					dsOut = new ObjectOutputStream(dsSocket.getOutputStream());
					dsOut.flush();
					dsIn = new ObjectInputStream(dsSocket.getInputStream());

					// Send message format: command (java byte), slamIp (java
					// UTF), slamPort (java int)

					dsOut.writeByte(RapidMessages.SLAM_REGISTER_DS);
					System.out.print("Please type slamIp: ");
					fromClient = stdIn.readLine();
					dsOut.writeUTF(fromClient); // slamIp
					System.out.print("Please type slamPort: ");
					fromClient = stdIn.readLine();
					dsOut.writeInt(Integer.parseInt(fromClient)); // slamIp
					dsOut.flush();

					// Receive message format: status (java byte)
					status = dsIn.readByte();
					System.out.println("Return Status: " + (status == RapidMessages.OK ? "OK" : "ERROR"));

					dsOut.close();
					dsIn.close();
					dsSocket.close();
					break;
				case RapidMessages.SLAM_GET_VMCPU_VMM:
					System.out.println("SLAM_GET_VMCPU_VMM");
					vmmSocket = new Socket(vmmAddress, vmmPort);
					vmmOut = new ObjectOutputStream(vmmSocket.getOutputStream());
					vmmOut.flush();
					vmmIn = new ObjectInputStream(vmmSocket.getInputStream());

					// Send message format: command (java byte), userId (java
					// long)

					vmmOut.writeByte(RapidMessages.SLAM_GET_VMCPU_VMM);
					System.out.print("Please type userId (type 0 for the total active VMs): ");
					fromClient = stdIn.readLine();
					vmmOut.writeLong(Long.parseLong(fromClient)); // userId
					vmmOut.flush();

					// Receive message format: status (java byte), vmcpuList
					// (java object)

					status = vmmIn.readByte();
					System.out.println("Return Status: " + (status == RapidMessages.OK ? "OK" : "ERROR"));
					if (status == RapidMessages.OK) {
						ArrayList<String> vmcpuList = (ArrayList<String>) vmmIn.readObject();
						Iterator<String> vmcpuListIterator = vmcpuList.iterator();
						System.out.println("Received CPU Util List: ");
						while (vmcpuListIterator.hasNext()) {
							System.out.println("userId:" + vmcpuListIterator.next());
							System.out.println("vmId:" + vmcpuListIterator.next());
							System.out.println("cpuUtil:" + vmcpuListIterator.next());
						}
					}
					vmmOut.close();
					vmmIn.close();
					vmmSocket.close();
					break;
				case RapidMessages.SLAM_GET_VMINFO_VMM:
					System.out.println("SLAM_GET_VMINFO_VMM");
					vmmSocket = new Socket(vmmAddress, vmmPort);
					vmmOut = new ObjectOutputStream(vmmSocket.getOutputStream());
					vmmOut.flush();
					vmmIn = new ObjectInputStream(vmmSocket.getInputStream());

					// Send message format: command (java byte), userId (java
					// long)

					vmmOut.writeByte(RapidMessages.SLAM_GET_VMINFO_VMM);
					System.out.print("Please type userId (type 0 for the total active VM list): ");
					fromClient = stdIn.readLine();
					vmmOut.writeLong(Long.parseLong(fromClient)); // userId
					vmmOut.flush();

					// Receive message format: status (java byte), vminfoList
					// (java object)

					status = vmmIn.readByte();
					System.out.println("Return Status: " + (status == RapidMessages.OK ? "OK" : "ERROR"));
					if (status == RapidMessages.OK) {
						ArrayList<String> vminfoList = (ArrayList<String>) vmmIn.readObject();
						Iterator<String> vminfoListIterator = vminfoList.iterator();
						System.out.println("Received CPU Info List: ");
						while (vminfoListIterator.hasNext()) {
							System.out.println("userId:" + vminfoListIterator.next());
							System.out.println("vmId:" + vminfoListIterator.next());
							System.out.println("vcpuNum:" + vminfoListIterator.next());
							System.out.println("memSize:" + vminfoListIterator.next());
							System.out.println("diskSize:" + vminfoListIterator.next());
							System.out.println("gpuCores:" + vminfoListIterator.next());
						}
					}
					vmmOut.close();
					vmmIn.close();
					vmmSocket.close();
					break;
				case RapidMessages.SLAM_CHANGE_VMFLV_VMM:
					System.out.println("SLAM_CHANGE_VMFLV_VMM");
					vmmSocket = new Socket(vmmAddress, vmmPort);
					vmmOut = new ObjectOutputStream(vmmSocket.getOutputStream());
					vmmOut.flush();
					vmmIn = new ObjectInputStream(vmmSocket.getInputStream());

					// Send message format: command (java byte), userId (java
					// long), vcpuNum (java int)

					vmmOut.writeByte(RapidMessages.SLAM_CHANGE_VMFLV_VMM);
					System.out.print("Please type userId: ");
					fromClient = stdIn.readLine();
					vmmOut.writeLong(Long.parseLong(fromClient)); // userId
					System.out.print("Please type vcpuNum: ");
					fromClient = stdIn.readLine();
					vmmOut.writeInt(Integer.parseInt(fromClient)); // vcpuNum
					System.out.print("Please type memSize: ");
					fromClient = stdIn.readLine();
					vmmOut.writeInt(Integer.parseInt(fromClient)); // memSize
					System.out.print("Please type diskSize: ");
					fromClient = stdIn.readLine();
					vmmOut.writeInt(Integer.parseInt(fromClient)); // diskSize

					vmmOut.flush();

					// Receive message format: status (java byte)

					status = vmmIn.readByte();
					System.out.println("Return Status: " + (status == RapidMessages.OK ? "OK" : "ERROR"));

					vmmOut.close();
					vmmIn.close();
					vmmSocket.close();
					break;
				case RapidMessages.SLAM_CONFIRM_VMFLV_VMM:
					System.out.println("SLAM_CONFIRM_VMFLV_VMM");
					vmmSocket = new Socket(vmmAddress, vmmPort);
					vmmOut = new ObjectOutputStream(vmmSocket.getOutputStream());
					vmmOut.flush();
					vmmIn = new ObjectInputStream(vmmSocket.getInputStream());

					// Send message format: command (java byte), userId (java
					// long)

					vmmOut.writeByte(RapidMessages.SLAM_CONFIRM_VMFLV_VMM);
					System.out.print("Please type userId: ");
					fromClient = stdIn.readLine();
					vmmOut.writeLong(Long.parseLong(fromClient)); // userId

					vmmOut.flush();

					// Receive message format: status (java byte)

					status = vmmIn.readByte();
					System.out.println("Return Status: " + (status == RapidMessages.OK ? "OK" : "ERROR"));

					vmmOut.close();
					vmmIn.close();
					vmmSocket.close();
					break;
				case RapidMessages.SLAM_GET_VMMEM_VMM:
					System.out.println("SLAM_GET_VMMEM_VMM");
					vmmSocket = new Socket(vmmAddress, vmmPort);
					vmmOut = new ObjectOutputStream(vmmSocket.getOutputStream());
					vmmOut.flush();
					vmmIn = new ObjectInputStream(vmmSocket.getInputStream());

					// Send message format: command (java byte), userId (java
					// long)

					vmmOut.writeByte(RapidMessages.SLAM_GET_VMMEM_VMM);
					System.out.print("Please type userId (type 0 for the total active VMs): ");
					fromClient = stdIn.readLine();
					vmmOut.writeLong(Long.parseLong(fromClient)); // userId
					vmmOut.flush();

					// Receive message format: status (java byte), vmcpuList
					// (java object)

					status = vmmIn.readByte();
					System.out.println("Return Status: " + (status == RapidMessages.OK ? "OK" : "ERROR"));
					if (status == RapidMessages.OK) {
						ArrayList<String> vmmemList = (ArrayList<String>) vmmIn.readObject();
						Iterator<String> vmmemListIterator = vmmemList.iterator();
						System.out.println("Received Mem Util List: ");
						while (vmmemListIterator.hasNext()) {
							System.out.println("userId:" + vmmemListIterator.next());
							System.out.println("vmId:" + vmmemListIterator.next());
							System.out.println("memory:" + vmmemListIterator.next());
							System.out.println("memoryusage:" + vmmemListIterator.next());
						}
					}
					vmmOut.close();
					vmmIn.close();
					vmmSocket.close();
					break;
				case RapidMessages.SLAM_GET_VMDISK_VMM:
					System.out.println("SLAM_GET_VMDISK_VMM");
					vmmSocket = new Socket(vmmAddress, vmmPort);
					vmmOut = new ObjectOutputStream(vmmSocket.getOutputStream());
					vmmOut.flush();
					vmmIn = new ObjectInputStream(vmmSocket.getInputStream());

					// Send message format: command (java byte), userId (java
					// long)

					vmmOut.writeByte(RapidMessages.SLAM_GET_VMDISK_VMM);
					System.out.print("Please type userId (type 0 for the total active VMs): ");
					fromClient = stdIn.readLine();
					vmmOut.writeLong(Long.parseLong(fromClient)); // userId
					vmmOut.flush();

					// Receive message format: status (java byte), vmcpuList
					// (java object)

					status = vmmIn.readByte();
					System.out.println("Return Status: " + (status == RapidMessages.OK ? "OK" : "ERROR"));
					if (status == RapidMessages.OK) {
						ArrayList<String> vmdiskList = (ArrayList<String>) vmmIn.readObject();
						Iterator<String> vmdiskListIterator = vmdiskList.iterator();
						System.out.println("Received Disk Util List: ");
						while (vmdiskListIterator.hasNext()) {
							System.out.println("userId:" + vmdiskListIterator.next());
							System.out.println("vmId:" + vmdiskListIterator.next());
							System.out.println("diskcapacity:" + vmdiskListIterator.next());
							System.out.println("diskallocation:" + vmdiskListIterator.next());
						}
					}
					vmmOut.close();
					vmmIn.close();
					vmmSocket.close();
					break;
				}

			} catch (Exception e) {
				System.err.println("Caught Exception: " + e.getMessage());
				e.printStackTrace();
			}

		} while (true);

	}

}
