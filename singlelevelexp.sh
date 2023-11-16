#Usage: clusterexp.sh [policy] [limit] [config]
PERNODECAP=$(($2/4))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
python3 ClusterController.py --policy $1 --limit $2 --duration 135 > $3_$1_fair_$2.csv&
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh fair 120 "$PERNODECAP" "$3"_singlelevel" &
ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh fair 120 "$PERNODECAP" "$3"_singlelevel" &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh fair 120 "$PERNODECAP" "$3"_singlelevel" &
ssh hwnam831@ow5 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh fair 120 "$PERNODECAP" "$3"_singlelevel" &
cd /mydata/workspace/faas-profiler;
./WorkloadInvoker -c $3.json & sleep 180;