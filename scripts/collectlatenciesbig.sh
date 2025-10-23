mkdir -p bigcluster

for i in $(seq 2 33); do
    echo "ow"$i
    rsync -av hwnam831@ow"$i":/mydata/workspace/jrapl/bigcluster\*.csv bigcluster/ &
done