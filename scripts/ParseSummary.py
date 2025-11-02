import pandas as pd
import glob
from scipy.stats import gmean
import os

def process_csv_files(file_pattern: str):
    """
    Finds CSV files matching a pattern, calculates the row-wise geometric mean
    of numeric columns, and saves the result to a new CSV file.

    Args:
        file_pattern (str): The glob pattern to match CSV files (e.g., 'bigcluster*.csv').
    """
    # Find all files matching the specified pattern
    csv_files = glob.glob(file_pattern)
    csv_files.sort(reverse=True)
    if not csv_files:
        print(f"No files found matching the pattern: {file_pattern}")
        return

    print(f"Found {len(csv_files)} files to process...")
    avg_df = pd.DataFrame()
    tail_df = pd.DataFrame()
    for file_path in csv_files:
        try:
            print(f"\nProcessing file: {file_path}")
            filename_parts = file_path[:-4].split('-')
            plimit = filename_parts[1]
            valuetype = filename_parts[2]
            # Read the CSV file into a pandas DataFrame
            df = pd.read_csv(file_path)
            if 'Policy' not in avg_df.columns:
                avg_df['PowerLimit'] = df['Policy']
                #avg_df.set_index('Policy', inplace=True)
                tail_df['PowerLimit'] = df['Policy']
                #tail_df.set_index('Policy', inplace=True)



            # Select only the numeric columns for the calculation
            # This prevents errors if your CSV has non-numeric columns (e.g., labels)
            numeric_cols = df.select_dtypes(include='number')

            if numeric_cols.empty:
                print(f"  - Warning: No numeric columns found in {file_path}. Skipping geomean calculation.")
                continue

            # Calculate the geometric mean for each row on the numeric columns.
            # The gmean function from scipy handles this efficiently.
            # We use a lambda function to handle rows that might contain zeros or negative numbers,
            # as the geometric mean is typically for positive numbers.
            # If a row contains a zero, the geometric mean is 0.
            df['geomean'] = numeric_cols.apply(
                lambda row: gmean(row) if (row > 0).all() else 0,
                axis=1
            )
            if valuetype == 'avg':
                avg_df[plimit] = df['geomean']
            elif valuetype == 'tail':
                tail_df[plimit] = df['geomean']
            
            # Create a new filename for the output
            base_name, ext = os.path.splitext(file_path)
            #output_path = f"{base_name}_geomean{ext}"

            # Save the updated DataFrame to a new CSV file
            # index=False prevents pandas from writing the DataFrame index as a column
            #df.to_csv(output_path, index=False)

            #print(f"  - Successfully created new file: {output_path}")

        except Exception as e:
            print(f"  - Error processing file {file_path}: {e}")
    avg_path=f"bigcluster_geomean_avg.csv"
    avg_df.T.to_csv(avg_path, index=True)
    tail_path=f"bigcluster_geomean_tail.csv"
    tail_df.T.to_csv(tail_path, index=True)

if __name__ == "__main__":
    # Define the pattern for the CSV files you want to process
    # This will match 'bigcluster1.csv', 'bigcluster_data.csv', etc.
    file_pattern_to_process = 'bigcluste*.csv'
    
    process_csv_files(file_pattern_to_process)
    
    print("\nAll files processed.")
