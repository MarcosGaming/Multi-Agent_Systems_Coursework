package smartphones_supply_chain_ontology.concepts;

import jade.content.onto.annotations.Slot;

public class Storage extends Component{
	
	private int capacity;
	
	@Slot(mandatory = true)
	public int getCapacity() {
		return capacity;
	}
	
	public void setCapacity(int capacity) {
		this.capacity = capacity;
	}

}
