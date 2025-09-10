#sudo cpupower frequency-set --governor userspace --min $1MHz --max $1MHz
echo passive | sudo tee /sys/devices/system/cpu/intel_pstate/status
MAXFREQ=$(cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq)
MINFREQ=$(cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq)
NCPUS=$(nproc)
echo $MAXFREQ"kHz"
for i in $(seq 0 $((NCPUS-1)))
do
    #sudo cpufreq-set -c $i -f $1MHz
    sudo cpufreq-set -c $i -g userspace
    sudo cpufreq-set -c $i -d $MAXFREQ -u $MINFREQ
    sudo cpufreq-set -c $i -g powersave
done
echo active | sudo tee /sys/devices/system/cpu/intel_pstate/status