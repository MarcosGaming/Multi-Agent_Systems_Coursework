package smartphones_supply_chain;

import java.util.ArrayList;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import smartphones_supply_chain_ontology.concepts.Battery;
import smartphones_supply_chain_ontology.concepts.Component;
import smartphones_supply_chain_ontology.concepts.Ram;
import smartphones_supply_chain_ontology.concepts.Screen;
import smartphones_supply_chain_ontology.concepts.Storage;
import smartphones_supply_chain_ontology.predicates.SupplierInformation;

public class Main {
	
	public static void main(String[] args) {
		Profile myProfile = new ProfileImpl();
		Runtime myRuntime = Runtime.instance();
		try{
			ContainerController myContainer = myRuntime.createMainContainer(myProfile);	
			AgentController rma = myContainer.createNewAgent("rma", "jade.tools.rma.rma", null);
			rma.start();
			
			// Suppliers information
			Object[] supplierInformation1 = new Object[] {new String[]{"5", "7"}, new int[]{100,150}, new int[]{64, 256}, new int[]{25,50}, 
					new int[]{4, 8}, new int[]{30,60}, new String[]{"2000", "3000"}, new int[]{70,100}, 1};
			Object[] supplierInformation2 = new Object[] {new String[]{}, new int[]{}, new int[]{64, 256}, new int[]{15,40}, 
					new int[]{4, 8}, new int[]{20,35}, new String[]{}, new int[]{}, 4};
			// Supplier agents
			AgentController supplierAgent1 = myContainer.createNewAgent("SupplierAgent1", SupplierAgent.class.getCanonicalName(), supplierInformation1);
			supplierAgent1.start();
			AgentController supplierAgent2 = myContainer.createNewAgent("SupplierAgent2", SupplierAgent.class.getCanonicalName(), supplierInformation2);
			supplierAgent2.start();
			// Day coordinator agent
			AgentController dayCoordinatorAgent = myContainer.createNewAgent("DayCoordinator", DayCoordinatorAgent.class.getCanonicalName(), null);
			dayCoordinatorAgent.start();
			// Manufacturer agent
			AgentController manufacturerAgent = myContainer.createNewAgent("Manufacturer", ManufacturerAgent.class.getCanonicalName(), null);
			manufacturerAgent.start();
			// Warehouse agent
			AgentController warehouseAgent = myContainer.createNewAgent("Warehouse", WarehouseAgent.class.getCanonicalName(), null);
			warehouseAgent.start();
			// Customer agents
			AgentController customerAgent1 = myContainer.createNewAgent("CutomerAgent1", CustomerAgent.class.getCanonicalName(), null);
			customerAgent1.start();
			AgentController customerAgent2 = myContainer.createNewAgent("CutomerAgent2", CustomerAgent.class.getCanonicalName(), null);
			customerAgent2.start();
			AgentController customerAgent3 = myContainer.createNewAgent("CutomerAgent3", CustomerAgent.class.getCanonicalName(), null);
			customerAgent3.start();
		}
		catch(Exception e){
			System.out.println("Exception starting agent: " + e.toString());
		}
	}
}
