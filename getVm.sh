# Assumes a working KVM/libvirt environment
#
# Install:
#   Add this bash function to your ~/.bashrc and `source ~/.bashrc`.
# Usage: 
#   $ virt-addr vm-name
#   192.0.2.16
#

    VM="$1"
    t=$(ps -ef | grep "qemu" | grep $VM | awk '{print $2}')
    if [ -z "$t" ];
    then
	printf "null"
    else
	printf $t
    fi

