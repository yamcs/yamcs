# Tenma remote control syntax (v2)

Source: https://bit.ly/2MT5360

## Command format
  ```
  VSET<X>:<NR2>
  ```

  * VSET: command header
  * X: output channel
  * : separator
  * NR2: parameter
  
## Command Details:
```
ISET<X>:<NR2>   
Description：Sets the output current.
Example: ISET1:2.225
Sets the CH1 output current to 2.225A
```
```
ISET<X>?
Description： Returns the output current setting.
Example: ISET1?
Returns the CH1 output current setting.
```

```
VSET<X>:<NR2>
Description：Sets the output voltage.
Example: VSET1:20.50
Sets the CH1 voltage to 20.50V
```

```
VSET<X>?
Description：Returns the output voltage setting.
Example: VSET1?
Returns the CH1 voltage setting
```

```
IOUT<X>?
Description：Returns the actual output current.
Example: IOUT1?
Returns the CH1 output current
```

```
VOUT<X>?
Description：Returns the actual output voltage.
Example: VOUT1?
Returns the CH1 output voltage
```

```
BEEP<Boolean>
Description：Turns on or off the beep. Boolean: boolean logic.
Example BEEP1
Turns on the beep.
```

```
OUT<Boolean>
Description：Turns on or off the output.
Boolean：0 OFF,1 ON
Example: OUT1    Turns on the output
```

```
STATUS?
Description：Returns the POWER SUPPLY status.
Contents 8 bits in the following format
Bit Item Description
0 CH1 0=CC mode, 1=CV mode
1 CH2 0=CC mode, 1=CV mode
2, 3 Tracking 00=Independent, 01=Tracking series,11=Tracking parallel
4 Beep 0=Off, 1=On
5 Lock 0=Lock, 1=Unlock
6 Output 0=Off, 1=On
7 N/A N/A
```

```
*IDN?
Description：Returns the KA3005P identification.
Example: *IDN?
Contents TENMA 72‐2535 V2.0 (Manufacturer, model name,).
```

```
RCL<NR1>
Description：Recalls a panel setting.
NR1 1 – 5: Memory number 1 to 5
Example: RCL1
Recalls the panel setting stored in memory number 1
```

```
SAV<NR1>
Description：Stores the panel setting.
NR1 1 – 5: Memory number 1 to 5
Example：SAV1 
Stores the panel setting in memory number 1
```

```
OCP<Boolean>
Description：Stores the panel setting.
Boolean：0 OFF,1 ON
Example: OCP1    Turns on the OCP
```

```
OVP<Boolean>
Description：Turns on the OVP.
Boolean：0 OFF,1 ON
Example: OVP1
Turns on the OVP
```