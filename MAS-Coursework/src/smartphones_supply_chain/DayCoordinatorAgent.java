package smartphones_supply_chain;

import jade.content.ContentElement;
import jade.content.lang.Codec;
import jade.content.lang.Codec.CodecException;
import jade.content.lang.sl.SLCodec;
import jade.content.onto.Ontology;
import jade.content.onto.OntologyException;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import smartphones_supply_chain_ontology.SupplyChainOntology;
import smartphones_supply_chain_ontology.predicates.DayEnd;
import smartphones_supply_chain_ontology.predicates.EndSimulation;
import smartphones_supply_chain_ontology.predicates.NewDay;

public class DayCoordinatorAgent extends Agent{
	
	private Codec codec = new SLCodec();
	private Ontology ontology = SupplyChainOntology.getInstance();
	
	private AID manufacturerAID;
	private AID warehouseAID;
	private AID[] customersAID;
	private AID[] suppliersAID;
	
	private static final int NUM_DAYS = 100;
	
	// Initialise the agent
	protected void setup() {
		getContentManager().registerLanguage(codec);
		getContentManager().registerOntology(ontology);
		// Register agent into the yellow pages
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("DayCoordinator");
		sd.setName(getLocalName() + "-dayCoordinator-agent");
		dfd.addServices(sd);
		try {
			DFService.register(this, dfd);
		} catch(FIPAException e) {
			e.printStackTrace();
		}
		// Wait for the other agents to initialise
		doWait(2000);
		// Add starter behaviours
		this.addBehaviour(new FindManufacturerBehaviour());
		this.addBehaviour(new FindWarehouseBehaviour());
		this.addBehaviour(new FindCustomersBehaviour());
		this.addBehaviour(new FindSuppliersBehaviour());
		this.addBehaviour(new SyncAgentsBehaviour());
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
	
	// Behaviour to find the manufacturer agent in the yellow pages
	private class FindManufacturerBehaviour extends OneShotBehaviour{
		public void action() {
			DFAgentDescription manufacturerTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Manufacturer");
			manufacturerTemplate.addServices(sd);
			try {
				manufacturerAID = new AID();
				DFAgentDescription[] manufacturerAgents = DFService.search(myAgent, manufacturerTemplate);
				manufacturerAID = manufacturerAgents[0].getName();
			} catch(FIPAException e) {
				e.printStackTrace();
			}
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
	
	// Behaviour to find the customer agents in the yellow pages
	private class FindCustomersBehaviour extends OneShotBehaviour{
		public void action() {
			DFAgentDescription customerTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Customer");
			customerTemplate.addServices(sd);
			try {
				DFAgentDescription[] customerAgents = DFService.search(myAgent, customerTemplate);
				int size = customerAgents.length;
				customersAID = new AID[size];
				for(int i = 0; i < size; i++) {
					customersAID[i] = customerAgents[i].getName();
				}
			} catch(FIPAException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Behaviour to find the supplier agents in the yellow pages
	private class FindSuppliersBehaviour extends OneShotBehaviour{
		public void action() {
			DFAgentDescription supplierTemplate = new DFAgentDescription();
			ServiceDescription sd = new ServiceDescription();
			sd.setType("Supplier");
			supplierTemplate.addServices(sd);
			try {
				DFAgentDescription[] supplierAgents = DFService.search(myAgent, supplierTemplate);
				int size = supplierAgents.length;
				suppliersAID = new AID[size];
				for(int i = 0; i < size; i++) {
					suppliersAID[i] = supplierAgents[i].getName();
				}
			} catch(FIPAException e) {
				e.printStackTrace();
			}
		}
	}
	
	// Behaviour to synchronize agents
	public class SyncAgentsBehaviour extends Behaviour{
		private int step = 0;
		private int currentDay = 1;
		private int currentRepetition = 1;
		
		@Override
		public void action() {
			switch(step) {
			case 0:
				// Create new day predicate
				NewDay newDay = new NewDay();
				newDay.setDayNumber(currentDay);
				// Send new day message to each agent
				ACLMessage msgNewDay = new ACLMessage(ACLMessage.INFORM);
				msgNewDay.setLanguage(codec.getName());
				msgNewDay.setOntology(ontology.getName());
				msgNewDay.addReceiver(manufacturerAID);
				msgNewDay.addReceiver(warehouseAID);
				for(AID aid : customersAID) {
					msgNewDay.addReceiver(aid);
				}
				for(AID aid : suppliersAID) {
					msgNewDay.addReceiver(aid);
				}
				try {
					// Convert from java objects to string
					getContentManager().fillContent(msgNewDay, newDay);
					myAgent.send(msgNewDay);
				} catch (CodecException ce) {
					 ce.printStackTrace();
				} catch (OntologyException oe) {
				 oe.printStackTrace();
				} 
				step++;
				currentDay++;
				break;
			case 1:
				// Wait for message from manufacturer to arrive
				MessageTemplate mt = MessageTemplate.and(MessageTemplate.MatchPerformative(ACLMessage.INFORM), MessageTemplate.MatchSender(manufacturerAID));
				ACLMessage msg = myAgent.receive(mt);
				if(msg != null) {
					try {
						ContentElement ce = getContentManager().extractContent(msg);
						if(ce instanceof DayEnd) {
							System.out.println("End of day: " + currentDay);
							step++;
						}
					} catch (CodecException ce) {
						ce.printStackTrace();
					} catch (OntologyException oe) {
						oe.printStackTrace();
					}
				} else {
					block();
				}
				break;
			}
		}
		
		@Override
		public boolean done() {
			return step == 2;
		}
		
		@Override
		public void reset() {
			super.reset();
			step = 0;
		}
		
		@Override
		public int onEnd() {
			if(currentDay == NUM_DAYS) {
				// Create end simulation predicate
				EndSimulation endSimulation = new EndSimulation();
				// Send end simulation message to each agent
				ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
				msg.setLanguage(codec.getName());
				msg.setOntology(ontology.getName());
				msg.addReceiver(manufacturerAID);
				msg.addReceiver(warehouseAID);
				for(AID aid : customersAID) {
					msg.addReceiver(aid);
				}
				for(AID aid : suppliersAID) {
					msg.addReceiver(aid);
				}
				try {
					// Convert from java objects to string
					getContentManager().fillContent(msg, endSimulation);
					myAgent.send(msg);
				} catch (CodecException ce) {
					 ce.printStackTrace();
				} catch (OntologyException oe) {
				 oe.printStackTrace();
				} 
				// Delete this agent
				myAgent.doDelete();
			} else {
				reset();
				myAgent.addBehaviour(this);
			}
			return 0;
		}
	}

}
