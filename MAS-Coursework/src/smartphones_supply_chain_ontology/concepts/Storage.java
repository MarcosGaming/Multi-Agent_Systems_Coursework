package smartphones_supply_chain_ontology.concepts;

import jade.content.onto.annotations.Slot;

public class Storage extends Component{
	
	private String capacity;
	
	@Slot(mandatory = true, permittedValues = {"64", "256"})
	public String getCapacity() {
		return capacity;
	}
	
	public void setCapacity(String capacity) {
		this.capacity = capacity;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((capacity == null) ? 0 : capacity.hashCode());
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
		if (capacity == null) {
			if (other.capacity != null)
				return false;
		} else if (!capacity.equals(other.capacity))
			return false;
		return true;
	}
}
