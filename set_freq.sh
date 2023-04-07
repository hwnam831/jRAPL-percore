#sudo cpupower frequency-set --governor userspace --min $1MHz --max $1MHz
for i in {0..19}
do
    sudo cpufreq-set -c $i -f $1MHz
    #sudo cpufreq-set -c $i -d $1MHz -u $1MHz
done
