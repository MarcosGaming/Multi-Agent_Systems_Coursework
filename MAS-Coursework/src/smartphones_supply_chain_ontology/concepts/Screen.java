package smartphones_supply_chain_ontology.concepts;

import jade.content.onto.annotations.Slot;

public class Screen extends Component{
	
	private int size;
	
	@Slot(mandatory = true)
	public int getSize() {
		return size;
	}
	
	public void setSize(int size) {
		this.size = size;
	}

}
