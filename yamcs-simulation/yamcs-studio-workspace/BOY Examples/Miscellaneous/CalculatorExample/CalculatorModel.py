"""
The model of the calculator.

@author: Xihui Chen
"""

class CalculatorModel(object):
    
    def __init__(self, lcd):
        
        self.oper1 = '0' 
        self.oper2 = '0'
        self.operator = None
        self.lcd = lcd
        
    def setLCDText(self, text):
        self.lcd.setPropertyValue("text", text)
    
    def appendDigit(self, digit):
        '''
        Append a digit
        '''        
        if self.operator == None:            
            o = self.oper1
        else:
            o = self.oper2          
       
        o = ('' if o == '0' else o) + digit        
        
        if self.operator == None:            
            self.oper1 = o
        else:
            self.oper2 = o       
                      
        self.setLCDText(o)
    def setOperator(self, operator):
        self.oper1=self.lcd.getPropertyValue("text")
        self.operator = operator
        
    def calc(self):
        '''
        when equal is pressed
        '''
        r = None
        if self.operator == "+":
            r = float(self.oper1) + float(self.oper2)
        elif self.operator == "-":
            r = float(self.oper1) - float(self.oper2)
        elif self.operator == "*":
            r = float(self.oper1) * float(self.oper2)
        elif self.operator == "/":
            r = float(self.oper1) / float(self.oper2)
        self.operator=None
        self.oper1='0'
        self.oper2='0'
        if r != None:
            self.setLCDText('%g' % r)
            
            
        
            

        
        
        
        



