package smartphones_supply_chain_ontology.concepts;

import jade.content.onto.annotations.Slot;

public class Battery extends Component{
	
	private String charge;
	
	@Slot(mandatory = true, permittedValues = {"2000", "3000"})
	public String getCharge() {
		return charge;
	}
	
	public void setCharge(String charge) {
		this.charge = charge;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((charge == null) ? 0 : charge.hashCode());
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
		Battery other = (Battery) obj;
		if (charge == null) {
			if (other.charge != null)
				return false;
		} else if (!charge.equals(other.charge))
			return false;
		return true;
	}
	
	

}
