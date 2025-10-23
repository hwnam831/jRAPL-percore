import os
import pandas as pd
import glob
import sys
import numpy as np
from scipy.stats import gmean
     
def addGeomean(input_file):           
    df = pd.read_csv(input_file)


    # Select only the numeric columns for the calculation
    # This prevents errors if your CSV has non-numeric columns (e.g., labels)
    numeric_cols = df.select_dtypes(include='number')


    # Calculate the geometric mean for each row on the numeric columns.
    # The gmean function from scipy handles this efficiently.
    # We use a lambda function to handle rows that might contain zeros or negative numbers,
    # as the geometric mean is typically for positive numbers.
    # If a row contains a zero, the geometric mean is 0.
    df['geomean'] = numeric_cols.apply(
        lambda row: gmean(row) if (row > 0).all() else 0,
        axis=1
    )
    
    # Save the file back
    df.to_csv(input_file, index=False)

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
    latencylists = {}
    # Process each CSV file
    for csv_file in csv_files:
        
        # Read the CSV file
        df = pd.read_csv(csv_file)
        latencies = df['Elapsed']
        bsize = df['Batchsize']
        filename_parts = csv_file[:-4].split('_')
        csvparts = str.join(',',filename_parts)
        # [hyperparameter, lr1, period2000, alpha0.1, 130w, 130, cnn-serving]
        configname = filename_parts[0][:-1] + '-' + filename_parts[4]
        policy = filename_parts[1] + '-' + filename_parts[2] + '-' + filename_parts[3]
        app = filename_parts[-2]
        print(app)

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
        addGeomean('../'+confname + '-avg.csv')
        with open('../'+confname + '-tail.csv', 'w') as file:
            file.write(f"Policy,{appstr}\n")
            policynames = list(mydata[confname].keys())
            policynames.sort()
            for policy in policynames:
                taillatencies = ",".join([str(np.quantile(mydata[confname][policy][app],0.95)) for app in appnames])
                file.write(f"{policy},{taillatencies}\n")
        addGeomean('../'+confname + '-tail.csv')    


if __name__ == "__main__":
    # Replace this with your folder path
    myfolder='hyperparameter'
    if len(sys.argv) >= 2:
        myfolder=sys.argv[1]
    print_b2p(myfolder)
