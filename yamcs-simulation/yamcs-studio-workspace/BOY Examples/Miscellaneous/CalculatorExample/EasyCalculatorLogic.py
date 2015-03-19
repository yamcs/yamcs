from org.csstudio.opibuilder.scriptUtil import PVUtil

op1=PVUtil.getDouble(pvs[0])
op2=PVUtil.getDouble(pvs[1])

operator = PVUtil.getString(pvs[2])

resultPV=pvs[3]

if operator=="+":
    resultPV.setValue(op1 + op2)
elif operator=="-":
    resultPV.setValue(op1 - op2)    
elif operator=="*":
    resultPV.setValue(op1 * op2)
elif operator=="/":
    resultPV.setValue(op1 / op2)