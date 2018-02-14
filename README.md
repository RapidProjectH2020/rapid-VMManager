# The VM Manager for the RAPID project

In the RAPID architecture, the VM Manager (VMM) is responsible for managing the computational resources of a physical machine. A VMM exists per single physical machine and monitors the computational resources including CPU, memory, and GPUs. Based on this responsibility, the VMM provides the following functionalities to the Directory Server (DS), the Service-Level Agreement Manager (SLAM), and the Acceleration Server (AS).
The first task of the VMM is to periodically monitor the CPU, memory, and GPU usage of the physical machine and informs the DS of the monitored results. The Resource Monitor in the VMM observes the CPU, memory, and GPU usage of the physical machine, and the VMM Engine in the VMM sends the information periodically to the DS.
The second job is to start or resume a VM by the request of the SLAM or the AS. When a client wants to get a VM from the physical machine, the Acceleration Client (AC) requests a VM to the SLAM. Upon the request, the SLAM notifies the VMM to launch a new VM or to resume a suspended VM. The VMM Engine is responsible for communication between the SLAM and the AS. The VM Lifetime Manager in the VMM then starts a new VM or resumes an existing VM.

Before building the source code, the user may need to change the configuration files according to the local system. In the “src” folder, there is a configuration file to modify: configuration.xml. The file contains VMM configuration settings. The transcription of the file is as follows:

* vmmPort denotes the server port that the VMM uses.
* vmmMaxConnection denotes the size of the thread pool that the ThreadPooledServer class
creates.
* vmmIpAddress denotes the IP address of the VMM.
* openStackSupport denotes whether the VMM supports OpenStack. If this value is 1, the
OpenStack support is enabled. Otherwise, libvirt is used for managing the VMs. If OpenStack is used, the specific OpenStack information including defaultFlavorId and authAddress should be provided. This information can be obtained from the administrator of the cloud infrastructure.
* normalVmImagePath indicates the location of the RAPID normal VM image that the Lifetime Manager will create. This information is only used for libvirt.
* helperVmImagePath indicates the location of the RAPID helper VM image that the Lifetime Manager will create. This information is only used for libvirt.
* gpuCores denotes the total number of cores that the physical GPU has.
* nvidiaSmiPath denotes the path of the nvidia-smi tool provided by NVIDIA.
* gpuDeviceNum indicates the main GPU device number in the system. The VMM supports only
one GPU device at this moment.
* dataPath denotes a directory used by the VMM for data persistence.
* ipShellPath denotes a directory and file path that is used in the VMM Engine to know the IP
address of a certain VM.

To simplify the installation process, we use Ant to compile the source code. Ant reads the build.xml file in the root directory and sets up the compiling environment. To compile the source code, just typing “$ant” in the root directory is required. The source code of the VMM will be then compiled and the binary files will be placed at the "bin” directory.

The “java eu.rapid.vmm.VMManager” command in the bin directory will execute the main function of the VMM after compilation. To execute the VMM, the jar files at the lib directory need to be added to the Java classpath.
