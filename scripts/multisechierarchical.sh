#Usage: clusterexp.sh [policy] [limit] [duration] [sec]
TOTALCAP=$(($2*15))
DURATION=$(($3+120))
PERIOD=$(($4*1000))
#cd /mydata/workspace/faas-profiler;
#./WorkloadInvoker -c warmup.json & sleep 70;
cd /mydata/workspace/jrapl;
mkdir -p results;
#LLAMA
ssh hwnam831@ow2 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" llama-3.1-8b llama-3.1-8b bigcluster"$4"sec1_"$1"_hierarchical high med 1" 2> /dev/null &
ssh hwnam831@ow3 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" llama-3.1-8b llama-3.1-8b bigcluster"$4"sec2_"$1"_hierarchical med low 2" 2> /dev/null &
ssh hwnam831@ow4 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" llama-3.1-8b llama-3.1-8b bigcluster"$4"sec3_"$1"_hierarchical high low 3" 2> /dev/null &
ssh hwnam831@ow5 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" llama-3.1-8b llama-3.1-8b bigcluster"$4"sec4_"$1"_hierarchical high low 4" 2> /dev/null &
#SD
ssh hwnam831@ow6 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" stable-diffusion stable-diffusion bigcluster"$4"sec1_"$1"_hierarchical high med 1" 2> /dev/null &
ssh hwnam831@ow7 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" stable-diffusion stable-diffusion bigcluster"$4"sec2_"$1"_hierarchical med low 2" 2> /dev/null &
ssh hwnam831@ow8 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" stable-diffusion stable-diffusion bigcluster"$4"sec3_"$1"_hierarchical high low 3" 2> /dev/null &
ssh hwnam831@ow9 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" stable-diffusion stable-diffusion bigcluster"$4"sec4_"$1"_hierarchical high low 4" 2> /dev/null &
#RESNET
ssh hwnam831@ow10 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" cnn-serving cnn-serving bigcluster"$4"sec1_"$1"_hierarchical high med 1" 2> /dev/null &
ssh hwnam831@ow11 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" cnn-serving cnn-serving bigcluster"$4"sec2_"$1"_hierarchical med low 2" 2> /dev/null &
#ssh hwnam831@ow12 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" cnn-serving cnn-serving bigcluster"$4"sec3_"$1"_hierarchical high low 3" 2> /dev/null &
ssh hwnam831@ow13 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" cnn-serving cnn-serving bigcluster"$4"sec4_"$1"_hierarchical high low 4" 2> /dev/null &
#VITS
ssh hwnam831@ow14 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" vits-ljs vits-ljs bigcluster"$4"sec1_"$1"_hierarchical high med 1" 2> /dev/null &
ssh hwnam831@ow15 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" vits-ljs vits-ljs bigcluster"$4"sec2_"$1"_hierarchical med low 2" 2> /dev/null &
ssh hwnam831@ow16 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" vits-ljs vits-ljs bigcluster"$4"sec3_"$1"_hierarchical high low 3" 2> /dev/null &
ssh hwnam831@ow17 "cd /mydata/workspace/jrapl; bash run_hierarchical.sh "$DURATION" "$2" vits-ljs vits-ljs bigcluster"$4"sec4_"$1"_hierarchical high low 4" 2> /dev/null &
python3 ClusterController.py --policy $1 --limit $TOTALCAP --duration $DURATION --periodms $PERIOD > results/bigcluster"$4"sec_hierarchical_$1_$2.csv;
