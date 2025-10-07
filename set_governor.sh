#sudo cpupower frequency-set --governor userspace --min $1MHz --max $1MHz
#echo passive | sudo tee /sys/devices/system/cpu/intel_pstate/status
NCPUS=$(nproc)
for i in $(seq 0 $((NCPUS-1)))
do
    sudo cpufreq-set -c $i -g $1
done
