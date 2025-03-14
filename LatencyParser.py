import os
import pandas as pd
import glob
import sys

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
        
                


if __name__ == "__main__":
    # Replace this with your folder path
    myfolder='latencies'
    if len(sys.argv) >= 2:
        myfolder=sys.argv[1]
    print_b2p(myfolder)
