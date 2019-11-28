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

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + capacity;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		Storage other = (Storage) obj;
		if (capacity != other.capacity)
			return false;
		return true;
	}
	
	

}
