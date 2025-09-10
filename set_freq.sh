#sudo cpupower frequency-set --governor userspace --min $1MHz --max $1MHz
echo passive | sudo tee /sys/devices/system/cpu/intel_pstate/status
NCPUS=$(nproc)
for i in $(seq 0 $((NCPUS-1)))
do
    #sudo cpufreq-set -c $i -f $1MHz
    sudo cpufreq-set -c $i -g userspace
    sudo cpufreq-set -c $i -d $1MHz -u $1MHz
    sudo cpufreq-set -c $i -f $1MHz
done
