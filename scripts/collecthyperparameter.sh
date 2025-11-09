mkdir -p hyperparameter

for i in $(seq 2 17); do
    echo "ow"$i
    rsync -av hwnam831@ow"$i":/mydata/workspace/jrapl/hyperparameter\*.csv hyperparameter/ &
done