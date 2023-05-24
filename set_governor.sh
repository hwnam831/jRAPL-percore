#sudo cpupower frequency-set --governor userspace --min $1MHz --max $1MHz
for i in {0..19}
do
    sudo cpufreq-set -c $i -g $1
done
