import os
import pandas as pd
import re
sheetnames = []
for fname in os.listdir():
    if fname[-4:] == '.csv':
        sheetnames.append(fname[:-4])
def postprocess(csvfile):
    df = pd.read_csv(csvfile)
    df = df.rename(columns={df.columns[1]:'power:0',df.columns[2]:'power:1',df.columns[4]:'freq:0',df.columns[5]:'freq:1',\
                        df.columns[7]:'powerpredict:0',df.columns[8]:'powerpredict:1',df.columns[16]:'bipspredict:0',df.columns[17]:'bipspredict:1',\
                  df.columns[10]:'grad:0',df.columns[11]:'grad:1', df.columns[13]:'bips:0',df.columns[14]:'bips:1',\
                  df.columns[19]:'powerlimit:0', df.columns[20]:'powerlimit:1',df.columns[22]:'newlimit:0', df.columns[23]:'newlimit:1',df.columns[25]:'timems'})
    
    df['B2/W:0'] = df['bips:0']*df['bips:0']/df['power:0']
    df['B2/W:1'] = df['bips:1']*df['bips:1']/df['power:1']
    df['B2/W:global'] = (df['bips:0']+df['bips:1'])**2/(df['power:1']+df['power:0'])
    #
    for i in range(len(df)-100,0,-1):
        if df['B2/W:0'][i] > 2.0 or df['B2/W:1'][i] > 2.0:
            print(i)
            df = df.iloc[10:i+1]
            break
    df = df.set_index('timems')
    return df.drop(columns=['Cur power usage', 'Freq', 'Prediction', 'Gradients', 'Cur perf', 'Bips Prediction', 'New power limit', 'Cur power limit', 'Time'])

xlsxnames = {}
os.system("rm *.xlsx")
for name in sheetnames:
    df = postprocess(name + '.csv')
    m = re.match(r"([a-zA-Z0-9]+)\_(.+)\_(\d+)",name)
    if m is None:
        print(name)
        continue
    wmode = 'w'
    if os.path.isfile(m.group(1) + '_' + m.group(3) + '.xlsx'):
        wmode = 'a'
    with pd.ExcelWriter(m.group(1) + '_' + m.group(3) + '.xlsx', mode=wmode) as writer:
        df.to_excel(writer, sheet_name=m.group(2), index_label='timems')
