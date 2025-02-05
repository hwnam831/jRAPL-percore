import pandas as pd
import os
import re

def process_csv(filepath):
    # Read the CSV file
    df = pd.read_csv(filepath)
    
    # Calculate averages for BIPS columns
    bips_0 = df[' BIPS:0']
    bips_1 = df[' BIPS:1']
    pow_0 = df[' CPU Power:0']
    pow_1 = df[' CPU Power:1']
    
    # Get the filename without extension to use for output
    base_filename = os.path.splitext(os.path.basename(filepath))[0]
        
    b2p_0 = bips_0**2 / pow_0
    b2p_1 = bips_1**2 / pow_1
    b2p = (bips_0+bips_1)**2 / (pow_0+pow_1)
    # Convert results to DataFrame and save as CSV
    print("{},{},{},{},{},{},{},{}".format(
        base_filename, bips_0.mean(), bips_1.mean(),
        pow_0.mean(), pow_1.mean(), b2p_0.mean(), b2p_1.mean(), b2p.mean()
    ))

# Example usage:
if __name__ == "__main__":
    # Replace with your CSV file path
    csv_file = "your_file.csv"
    print("Filename,BIPS:0,BIPS:1,Power:0,Power:1,B2P:0,B2P:1,B2P")
    filenames = os.listdir()
    matcher = re.compile(r"local_(.+)_(.+)_(.+)_(.+).csv")
    for fname in filenames:
        if matcher.match(fname):
            process_csv(fname)