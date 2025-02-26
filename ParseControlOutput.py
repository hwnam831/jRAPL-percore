import os
import pandas as pd
import glob
import sys

def last_line_to_first(filename):
    """
    Moves the last line of a text file to become the first line.
    
    Args:
        filename: Path to the text file to modify
    """
    # Read all lines from the file
    with open(filename, 'r') as file:
        lines = file.readlines()
    
    # Check if the file has at least two lines
    if len(lines) < 2:
        return  # No change needed if file has 0 or 1 line
    elif 'Time(ms)' in lines[0]:
        return
    
    # Get the last line and remove it from the list
    last_line = lines.pop()
    
    # Put the last line at the beginning
    lines.insert(0, last_line)
    
    # Write the modified content back to the file
    with open(filename, 'w') as file:
        file.writelines(lines)

def print_b2p(folder_path):
    """
    Parse all CSV files in the specified folder and print the average
    of the 'BIPS' column for each file along with its filename.
    
    Args:
        folder_path (str): Path to the folder containing CSV files
    """
    # Get a list of all CSV files in the folder
    csv_files = glob.glob(os.path.join(folder_path, "*.csv"))
    
    if not csv_files:
        print(f"No CSV files found in {folder_path}")
        return
    
    # Process each CSV file
    for csv_file in csv_files:
        last_line_to_first(csv_file)
        # Read the CSV file
        df = pd.read_csv(csv_file)
        bipscols = []
        powcols = []

        for node in (1,2,3,4):
            for soc in (0,1):
                bipscols.append(df[f"BIPS:{node}:{soc}"])
                powcols.append(df[f"Consumption:{node}:{soc}"])

        totalbips = bipscols[0] + bipscols[1] + bipscols[2] + bipscols[3] + \
                    bipscols[4] + bipscols[5] + bipscols[6] + bipscols[7]
        totalpower = powcols[0] + powcols[1] + powcols[2] + powcols[3] + \
                    powcols[4] + powcols[5] + powcols[6] + powcols[7]
        geombips = bipscols[0] * bipscols[1] * bipscols[2] * bipscols[3] * \
                    bipscols[4] * bipscols[5] * bipscols[6] * bipscols[7]
        geompow = powcols[0] * powcols[1] * powcols[2] * powcols[3] * \
                    powcols[4] * powcols[5] * powcols[6] * powcols[7]
        geomb2p = (geombips ** (1/4)) / (geompow ** (1/8))
        totalb2p = totalbips**2 / totalpower

        # Check if 'BIPS' column exists
        b2p = totalbips*totalbips/totalpower
        filename_parts = csv_file[:-4].split('_')
        csvparts = str.join(',',filename_parts)
        bipsmean = [bc.mean() for bc in bipscols]
        powmean = [pow.mean() for pow in powcols]
        b2p = ','.join([str((bipsmean[2*i]+bipsmean[2*i+1])**2/(powmean[2*i]+powmean[2*i+1])) for i in range(4)])
        print(f"{csvparts},{totalb2p.mean()},{b2p},{geomb2p.mean()}")
                


if __name__ == "__main__":
    # Replace this with your folder path
    myfolder='results'
    if len(sys.argv) >= 2:
        myfolder=sys.argv[1]
    print_b2p(myfolder)