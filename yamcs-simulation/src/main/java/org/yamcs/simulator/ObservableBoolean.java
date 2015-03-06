package org.yamcs.simulator;


public class ObservableBoolean {
	private boolean currentState;
	private boolean newState;
	
	ObservableBoolean(boolean currentState, boolean newState ){
		
		this.currentState = currentState;
		this.newState = newState;
		
	}
	
	public boolean isCurrentState() {
		return currentState;
	}

	public void setCurrentState(boolean currentState) {
		this.currentState = currentState;
	}

	public boolean isNewState() {
		return newState;
	}

	public void setNewState(boolean newState) {
		this.newState = newState;
	}

	public boolean haschanged(){
		
		if (newState != currentState){
				
			return true;
			
		}else
			return false;
		
	}

}
