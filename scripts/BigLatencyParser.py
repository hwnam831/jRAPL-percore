import os
import pandas as pd
import glob
import sys
import numpy as np

def print_b2p(folder_path):
    """
    Parse all CSV files in the specified folder and print the average
    of the 'BIPS' column for each file along with its filename.
    
    Args:
        folder_path (str): Path to the folder containing CSV files
    """
    # Get a list of all CSV files in the folder
    os.chdir(folder_path)
    csv_files = glob.glob("*.csv")
    
    if not csv_files:
        print(f"No CSV files found in {folder_path}")
        return
    mydata = {}
    appnames = []
    # Process each CSV file
    for csv_file in csv_files:
        
        # Read the CSV file
        df = pd.read_csv(csv_file)
        latencies = df['Elapsed']
        avg = latencies.mean()
        tail = latencies.quantile(0.95)
        filename_parts = csv_file[:-4].split('_')
        csvparts = str.join(',',filename_parts)
        configname = filename_parts[0] + '-' + filename_parts[3]
        policy = filename_parts[1] + '-' + filename_parts[2]
        app = filename_parts[4] + '-' + filename_parts[5]
        if app not in appnames:
            appnames.append(app)
        if configname not in mydata:
            mydata[configname] = {}
        if policy not in mydata[configname]:
            mydata[configname][policy] = {}
        mydata[configname][policy][app] = (avg, tail)
        print(f"{csvparts},{avg},{tail}")
    for confname in mydata:
        with open('../'+confname + '-avg.csv', 'w') as file:
            appstr = ",".join(appnames)
            file.write(f"Policy,{appstr}\n")
            for policy in mydata[confname]:
                avglatencies = ",".join([str(mydata[confname][policy][app][0]) for app in appnames])
                file.write(f"{policy},{avglatencies}\n")
        with open('../'+confname + '-tail.csv', 'w') as file:
            file.write(f"Policy,{appstr}\n")
            for policy in mydata[confname]:
                taillatencies = ",".join([str(mydata[confname][policy][app][1]) for app in appnames])
                file.write(f"{policy},{taillatencies}\n")
     
def print_b2p2(folder_path):
    """
    Parse all CSV files in the specified folder and print the average
    of the 'BIPS' column for each file along with its filename.
    
    Args:
        folder_path (str): Path to the folder containing CSV files
    """
    # Get a list of all CSV files in the folder
    os.chdir(folder_path)
    csv_files = glob.glob("*.csv")
    
    if not csv_files:
        print(f"No CSV files found in {folder_path}")
        return
    mydata = {}
    appnames = []
    latencylists = {}
    # Process each CSV file
    for csv_file in csv_files:
        
        # Read the CSV file
        df = pd.read_csv(csv_file)
        latencies = df['Elapsed']
        bsize = df['Batchsize']
        filename_parts = csv_file[:-4].split('_')
        csvparts = str.join(',',filename_parts)
        configname = filename_parts[0][:-1] + '-' + filename_parts[3]
        policy = filename_parts[1] + '-' + filename_parts[2]
        app = filename_parts[4]

        if configname not in mydata:
            mydata[configname] = {}
        if policy not in mydata[configname]:
            mydata[configname][policy] = {}
        if app not in appnames:
            appnames.append(app)
        if app not in mydata[configname][policy]:
            mydata[configname][policy][app] = []
        latencylist = latencies.to_numpy().tolist()
        bsizelist = bsize.to_numpy().tolist()
        newlist = []
        for i in range(len(latencylist)):
            newlist += [latencylist[i]] * bsizelist[i]
        mydata[configname][policy][app] += newlist
        print(f"{csvparts}")
    appnames.sort()
    for confname in mydata:
        with open('../'+confname + '-avg.csv', 'w') as file:
            appstr = ",".join(appnames)
            file.write(f"Policy,{appstr}\n")
            policynames = list(mydata[confname].keys())
            policynames.sort()
            for policy in policynames:
                avglatencies = ",".join([str(np.array(mydata[confname][policy][app]).mean()) for app in appnames])
                file.write(f"{policy},{avglatencies}\n")
        with open('../'+confname + '-tail.csv', 'w') as file:
            file.write(f"Policy,{appstr}\n")
            policynames = list(mydata[confname].keys())
            policynames.sort()
            for policy in policynames:
                taillatencies = ",".join([str(np.quantile(mydata[confname][policy][app],0.95)) for app in appnames])
                file.write(f"{policy},{taillatencies}\n")                


if __name__ == "__main__":
    # Replace this with your folder path
    myfolder='bigcluster'
    if len(sys.argv) >= 2:
        myfolder=sys.argv[1]
    print_b2p2(myfolder)
