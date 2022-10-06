#sudo cpupower frequency-set --governor userspace --min $1 --max $1
for i in {0..19}
do
    sudo cpufreq-set -c $i -f $1MHz
done