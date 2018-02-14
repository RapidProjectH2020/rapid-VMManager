# Assumes a working KVM/libvirt environment
#
# Install:
#   Add this bash function to your ~/.bashrc and `source ~/.bashrc`.
# Usage: 
#   $ virt-addr vm-name
#   192.0.2.16
#

    VM="$1"
    t=$(arp -an | grep "`virsh -c qemu+tcp://127.0.0.1/system dumpxml $VM | grep "mac address" | sed "s/.*'\(.*\)'.*/\1/g"`" | awk '{ gsub(/[\(\)]/,"",$2); print $2 }')
    printf $t

