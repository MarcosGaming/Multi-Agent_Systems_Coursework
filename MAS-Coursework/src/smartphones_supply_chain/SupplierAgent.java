package smartphones_supply_chain;

import java.util.ArrayList;

import jade.content.Concept;
import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.content.lang.Codec.CodecException;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.content.onto.basic.Action;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import smartphones_supply_chain_ontology.SupplyChainOntology;
import smartphones_supply_chain_ontology.actions.SellSupplies;
import smartphones_supply_chain_ontology.concepts.Battery;
import smartphones_supply_chain_ontology.concepts.Component;
import smartphones_supply_chain_ontology.concepts.Ram;
import smartphones_supply_chain_ontology.concepts.Screen;
import smartphones_supply_chain_ontology.concepts.Storage;
import smartphones_supply_chain_ontology.concepts.Supplies;
import smartphones_supply_chain_ontology.predicates.NewDay;
import smartphones_supply_chain_ontology.predicates.NoMoreSuppliesToday;
import smartphones_supply_chain_ontology.predicates.SupplierDetails;
import smartphones_supply_chain_ontology.predicates.SupplierInformation;
import smartphones_supply_chain_ontology.predicates.SuppliesDelivered;

public class SupplierAgent extends Agent{

	private Codec codec = new SLCodec();
	private Ontology ontology = SupplyChainOntology.getInstance();
	
	private AID warehouseAID;
	private AID dayCoordinatorAID;
	
	private SupplierInformation supplierInformation;
	private ArrayList<SuppliesToDeliver> pendingDeliveries;
	
	// Initialise the agent
	protected void setup() {
		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);
		// Register agent into the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("Supplier");
		sd.setName(getLocalName() + "-supplier-agent");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch(FIPAException e) {
			e.printStackTrace();
		}
		// Get the supplier information from the arguments
		Object[] args = this.getArguments();
		supplierInformation = createSupplierInformation((String[])args[0], (int[])args[1], (String[])args[2], (int[])args[3], (String[])args[4], (int[])args[5], (String[])args[6], 
														(int[])args[7], (int)args[8]);
		// Initialise pending deliveries list
		pendingDeliveries = new ArrayList<SuppliesToDeliver>();
		// Wait for other agents to initialise
		doWait(2000);
		// Add starter behaviours
		this.addBehaviour(new FindWarehouseBehaviour());
		this.addBehaviour(new FindDayCoordinatorBehaviour());
		this.addBehaviour(new DayCoordinatorWaiterBehaviour());
		this.addBehaviour(new ProvideDetailsBehaviour());
		this.addBehaviour(new ProcessSuppliesRequestsBehaviour());
	}
	
	// Called when agent is deleted
	protected void takeDown() {
		// Bye message
		System.out.println("Agent " + this.getLocalName() + " is terminating.");
		// Deregister agent from the yellow pages
		try {
			DFService.deregister(this);
		} catch(FIPAException e) {
			e.printStackTrace();
		}
	}
	
	// Behaviour to find the warehouse agent in the yellow pages
	private class FindWarehouseBehaviour extends OneShotBehaviour{
		public void action() {
			DFAgentDescription warehouseTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Warehouse");
			warehouseTemplate.addServices(sd);
			try {
				warehouseAID = new AID();
				DFAgentDescription[] warehouseAgents = DFService.search(myAgent, warehouseTemplate);
				warehouseAID = warehouseAgents[0].getName();
			} catch(FIPAException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Behaviour to find the dayCoordinator agent in the yellow pages
	private class FindDayCoordinatorBehaviour extends OneShotBehaviour{
		public void action() {
			DFAgentDescription dayCoordinatorTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("DayCoordinator");
			dayCoordinatorTemplate.addServices(sd);
			try {
				dayCoordinatorAID = new AID();
				DFAgentDescription[] dayCoordinatorAgents = DFService.search(myAgent, dayCoordinatorTemplate);
				dayCoordinatorAID = dayCoordinatorAgents[0].getName();
			} catch(FIPAException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Behaviour that waits for new day or end simulation calls
	public class DayCoordinatorWaiterBehaviour extends CyclicBehaviour{
		public void action() {
			MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchSender(dayCoordinatorAID));
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				try {
					// Convert string to java objects
					ContentElement ce = getContentManager().extractContent(msg);
					// Every new day the supplier might send supplies to the warehouse
					if(ce instanceof NewDay) {
						// Add to the supplier the send supplies behaviour
						myAgent.addBehaviour(new SendSuppliesBehaviour());
					} else {
						// If the predicate is not NewDay then it is EndSimulation
						myAgent.doDelete();
					}
					
				} catch (CodecException ce) {
					ce.printStackTrace();
				} catch (OntologyException oe) {
					oe.printStackTrace();
				}
			} else {
				block();
			}
		}
	}
	
	// Behaviour that is going to provide to the sender with this agent details, this will only happen once
	public class ProvideDetailsBehaviour extends Behaviour{
		private boolean detailsSent = false;
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.QUERY_IF);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				try {
					// Convert string to java objects
					ContentElement ce =  getContentManager().extractContent(msg);
					if(ce instanceof SupplierDetails) {
						// Send a message to the manufacturer with the supplier information
						ACLMessage response = new ACLMessage(ACLMessage.INFORM);
						response.addReceiver(warehouseAID);
						response.setLanguage(codec.getName());
						response.setOntology(ontology.getName());
						try {
							// Convert java objects to string
							getContentManager().fillContent(response, supplierInformation);
							myAgent.send(response);
						} catch (CodecException codece) {
							codece.printStackTrace();
						} catch (OntologyException oe) {
							oe.printStackTrace();
						}
						detailsSent = true;
					}
				} catch (CodecException ce) {
					ce.printStackTrace();
				} catch (OntologyException oe) {
					oe.printStackTrace();
				}
			} else {
				block();
			}
		}
		
		public boolean done() {
			return detailsSent;
		}
	}
	
	// Behaviour that is going to process requests to sell supplies
	public class ProcessSuppliesRequestsBehaviour extends CyclicBehaviour{
		public void action() {
			MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.REQUEST);
			ACLMessage msg = myAgent.receive(mt);
			if(msg != null) {
				try {
					// Convert strings to java objects
					ContentElement ce = getContentManager().extractContent(msg);
					if(ce instanceof Action) {
						Concept action = ((Action)ce).getAction();
						if(action instanceof SellSupplies) {
							SellSupplies sellSupplies = (SellSupplies) action;
							// Add the supplies to the list of pending deliveries
							SuppliesToDeliver suppliesToDeliver = new SuppliesToDeliver();
							suppliesToDeliver.setDaysLeftToDeliver(supplierInformation.getDeliveryTime());
							suppliesToDeliver.setSupplies(sellSupplies.getSupplies());
							pendingDeliveries.add(suppliesToDeliver);
						}
					}
				} catch (CodecException codece) {
					codece.printStackTrace();
				} catch (OntologyException oe) {
					oe.printStackTrace();
				}
			}
		}
	}
	
	// Behaviour that is going to send supplies if they are ready at the beginning of each day
	public class SendSuppliesBehaviour extends OneShotBehaviour{
		public void action() {
			ArrayList<SuppliesToDeliver> deliveriesToRemove = new ArrayList<SuppliesToDeliver>();
			for(SuppliesToDeliver delivery : pendingDeliveries) {
				int days = delivery.getDaysLeftToDeliver();
				days--;
				if(days == 0) {
					// Create predicate
					SuppliesDelivered suppliesDelivered = new SuppliesDelivered();
					suppliesDelivered.setSupplies(delivery.getSupplies());
					// Send supplies to warehouse
					ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
					msg.addReceiver(warehouseAID);
					msg.setLanguage(codec.getName());
					msg.setOntology(ontology.getName());
					try {
						getContentManager().fillContent(msg, suppliesDelivered);
						myAgent.send(msg);
					} catch (CodecException codece) {
						codece.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
					// Add delivery to the list of deliveries to remove
					deliveriesToRemove.add(delivery);
				} else {
					// Update days left to deliver
					delivery.setDaysLeftToDeliver(days);
				}
			}
			// Remove deliveries from list of pending deliveries
			pendingDeliveries.removeAll(deliveriesToRemove);
			// Create Predicate
			NoMoreSuppliesToday noMoreSuppliesToday = new NoMoreSuppliesToday();
			// Inform warehouse that there are no more supplies to deliver
			ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
			msg.addReceiver(warehouseAID);
			msg.setLanguage(codec.getName());
			msg.setOntology(ontology.getName());
			try {
				// Transform java objects to strings
				getContentManager().fillContent(msg, noMoreSuppliesToday);
				myAgent.send(msg);
			} catch (CodecException codece) {
				codece.printStackTrace();
			} catch (OntologyException oe) {
				oe.printStackTrace();
			}
		}
	}
	
	// Class for the supplies that need to be sent
	private class SuppliesToDeliver{
		private Supplies supplies;
		private int daysLeftToDeliver;
		
		public void setSupplies(Supplies supplies) {
			this.supplies = supplies;
		}
		
		public Supplies getSupplies() {
			return supplies;
		}
		
		public void setDaysLeftToDeliver(int daysLeftToDeliver) {
			this.daysLeftToDeliver = daysLeftToDeliver;
		}
		
		public int getDaysLeftToDeliver() {
			return daysLeftToDeliver;
		}
		
	}
	
	// Method that is going to extract the supplier information from the arguments
	private SupplierInformation createSupplierInformation(String[] screenSizes, int[] screenPrices, String[] storageCapacities, int[] storagePrices,
			String[] ramAmounts, int[] ramPrices, String[] batteryCharges, int[] batteryPrices, int deliveryTime) {
		SupplierInformation supplierInformation = new SupplierInformation();
		ArrayList<Component> components = new ArrayList<Component>();
		// Screens
		for(int i = 0; i < screenSizes.length; i++) {
			Screen screen = new Screen();
			screen.setSize(screenSizes[i]);
			screen.setPrice(screenPrices[i]);
			components.add(screen);
		}
		// Storages
		for(int i = 0; i < storageCapacities.length; i++) {
			Storage storage = new Storage();
			storage.setCapacity(storageCapacities[i]);
			storage.setPrice(storagePrices[i]);
			components.add(storage);
		}
		// Rams
		for(int i = 0; i < ramAmounts.length; i++) {
			Ram ram = new Ram();
			ram.setAmount(ramAmounts[i]);
			ram.setPrice(ramPrices[i]);
			components.add(ram);
		}
		// Batteries
		for(int i = 0; i < batteryCharges.length; i++) {
			Battery battery = new Battery();
			battery.setCharge(batteryCharges[i]);
			battery.setPrice(batteryPrices[i]);
			components.add(battery);
		}
		supplierInformation.setComponents(components);
		supplierInformation.setDeliveryTime(deliveryTime);
		return supplierInformation;
	}
}
