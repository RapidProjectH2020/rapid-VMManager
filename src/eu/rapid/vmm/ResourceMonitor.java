package eu.rapid.vmm;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;

import org.apache.log4j.Logger;

import com.sun.management.OperatingSystemMXBean;

public class ResourceMonitor {
	private Logger logger = Logger.getLogger(getClass());

	private static ResourceMonitor resourceMonitor = new ResourceMonitor();
	private OperatingSystemMXBean osBean = null;
	private String nvidiaSmiPath = "/usr/bin/nvidia-smi";
	private int gpuDeviceNum;

	private ResourceMonitor() {
		osBean = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
		osBean.getSystemCpuLoad();
	}

	public static ResourceMonitor getInstance() {
		return resourceMonitor;
	}

	/**
	 * The function reports the current total CPU usage.
	 * 
	 * @return current total CPU load. The value is between 0 and 100. 0 means
	 *         that the system is completely idle whereas 100 indicates that the
	 *         system is busy.
	 * 
	 */
	public double getSystemCpuLoad() {
		return osBean.getSystemCpuLoad() * 100;
	}

	/**
	 * The function reports the free amount of physical memory in MB.
	 * 
	 * @return free amount of physical memory in MB.
	 */
	public long getFreePhysicalMemorySize() {
		return osBean.getFreePhysicalMemorySize() / (1024 * 1024);
	}

	/**
	 * The function reports the total number of cores in the system.
	 * 
	 * @return total number of cores in the system.
	 */
	public int getNumberOfCPUCores() {

		String command = "";
		if (OSValidator.isUnix()) {
			command = "lscpu";
		} else if (OSValidator.isWindows()) {
			command = "cmd /C WMIC CPU Get /Format:List";
		} else if (OSValidator.isMac()) {
			command = "sysctl -n hw.ncpu";
		}

		Process process = null;
		int numberOfCores = 0;
		try {
			process = Runtime.getRuntime().exec(command);
		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line;
		try {
			while ((line = reader.readLine()) != null) {
				if (OSValidator.isUnix()) {
					if (line.contains("CPU(s):")) {
						numberOfCores = Integer.parseInt(line.split("\\s+")[line.split("\\s+").length - 1]);
						break;
					}
				} else if (OSValidator.isWindows()) {
					if (line.contains("NumberOfCores")) {
						numberOfCores = Integer.parseInt(line.split("=")[1]);
					}
				} else if (OSValidator.isMac()) {
					numberOfCores = Integer.parseInt(line);
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
		return numberOfCores;
	}

	/**
	 * The function reports the current total GPU usage.
	 * 
	 * @return current total GPU load. The value is between 0 and 100. 0 means
	 *         that the GPU is completely idle whereas 100 indicates that the
	 *         GPU is busy.
	 */
	public int getSystemGpuLoad() {
		String command = nvidiaSmiPath + " stats -i " + gpuDeviceNum + " -d gpuUtil -c 1";

		Process process = null;
		int gpuUtil = 0;
		try {
			process = Runtime.getRuntime().exec(command);
		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			//logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
			return gpuUtil;
		}

		BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
		String line;
		try {
			while ((line = reader.readLine()) != null) {
				String gpuUtilStr = line.split(",")[3].trim();
				gpuUtil = Integer.parseInt(gpuUtilStr);

			}
		} catch (Exception e) {
			String message = "";
			for (StackTraceElement stackTraceElement : Thread.currentThread().getStackTrace()) {
				message = message + System.lineSeparator() + stackTraceElement.toString();
			}
			//logger.error("Caught Exception: " + e.getMessage() + System.lineSeparator() + message);
			//e.printStackTrace();
			return gpuUtil;
		}
		return gpuUtil;
	}

	/**
	 * @return
	 */
	public String getNvidiaSmiPath() {
		return nvidiaSmiPath;
	}

	/**
	 * @param nvidiaSmiPath
	 */
	public void setNvidiaSmiPath(String nvidiaSmiPath) {
		this.nvidiaSmiPath = nvidiaSmiPath;
	}

	/**
	 * @return
	 */
	public int getGpuDeviceNum() {
		return gpuDeviceNum;
	}

	/**
	 * @param gpuDeviceNum
	 */
	public void setGpuDeviceNum(int gpuDeviceNum) {
		this.gpuDeviceNum = gpuDeviceNum;
	}
}

class OSValidator {

	private static String OS = System.getProperty("os.name").toLowerCase();

	public static boolean isWindows() {
		return (OS.indexOf("win") >= 0);
	}

	public static boolean isMac() {
		return (OS.indexOf("mac") >= 0);
	}

	public static boolean isUnix() {
		return (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0);
	}

	public static boolean isSolaris() {
		return (OS.indexOf("sunos") >= 0);
	}

	/**
	 * The function will report the type of the system OS.
	 * 
	 * @return type of the system OS
	 */
	public static String getOS() {
		if (isWindows()) {
			return "win";
		} else if (isMac()) {
			return "osx";
		} else if (isUnix()) {
			return "uni";
		} else if (isSolaris()) {
			return "sol";
		} else {
			return "err";
		}
	}
}
