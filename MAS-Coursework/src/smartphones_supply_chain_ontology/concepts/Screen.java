package smartphones_supply_chain_ontology.concepts;

import jade.content.onto.annotations.Slot;

public class Screen extends Component{
	
	private String size;
	
	@Slot(mandatory = true, permittedValues = {"5","7"})
	public String getSize() {
		return size;
	}
	
	public void setSize(String size) {
		this.size = size;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((size == null) ? 0 : size.hashCode());
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
		Screen other = (Screen) obj;
		if (size == null) {
			if (other.size != null)
				return false;
		} else if (!size.equals(other.size))
			return false;
		return true;
	}
	
	

}
