import java.util.ArrayList;

public class Route {
	private ArrayList<Edge> route;
	private ArrayList<Edge> prohibitedEdges;
	private Demand demand;
	private int FFE;
	private int FFErep;
	private boolean repair;
	private int lagrangeProfit;
	private int realProfit;

	public Route(Demand demand, boolean repair){
		this.route = new ArrayList<Edge>();
		this.demand = demand;
		this.repair = repair;
		this.prohibitedEdges = new ArrayList<Edge>();
		this.lagrangeProfit = 0;
		this.realProfit = 0;
		this.FFE = 0;
		this.FFErep = 0;
	}

	public void findRoute(){
		if(!route.isEmpty()){
			throw new RuntimeException("Tried to find a route for a non-empty Route.");
		}
		BellmanFord.runSingleRoute(this);
	}

	public void update(ArrayList<Edge> route){
		//TODO: Add 1000 to lagrangeProfit???
		int lagrangeProfit = demand.getRate();
		int realProfit = demand.getRate();
		for(Edge e : route){
			e.addRoute(this);
			lagrangeProfit -= e.getCost();
			realProfit -= e.getRealCost();
		}
		this.lagrangeProfit = lagrangeProfit;
		this.realProfit = realProfit;
		this.route = route;
	}

	public ArrayList<Edge> getProhibitedEdges() {
		return prohibitedEdges;
	}

	public void addProhibitedEdge(Edge prohibitedEdge) {
		this.prohibitedEdges.add(prohibitedEdge);
	}

	public int getFFE() {
		return FFE;
	}

	public void setFFE(int FFE) {
		this.FFE = FFE;
	}

	public int getFFErep() {
		return FFErep;
	}

	public void setFFErep(int FFErep) {
		this.FFErep = FFErep;
	}

	public void adjustFFErep(int adjustFFErep){
		this.FFErep += adjustFFErep;
		if(FFErep == 0){
			deleteRoute();
		}
	}

	public void deleteRoute(){
		for(Edge e : route){
			e.removeRoute(this);
		}
		demand.removeRoute(this);
	}

	public void setRoute(ArrayList<Edge> route){
		this.route = route;
	}

	public ArrayList<Edge> getRoute() {
		return route;
	}

	public Demand getDemand() {
		return demand;
	}

	public boolean isRepair() {
		return repair;
	}


	public int getLagrangeProfit() {
		return lagrangeProfit;
	}

	public void setLagrangeProfit(int lagrangeProfit) {
		this.lagrangeProfit = lagrangeProfit;
	}

	public int getRealProfit() {
		return realProfit;
	}

	public void setRealProfit(int realProfit) {
		this.realProfit = realProfit;
	}

	public String simplePrint(){
		String str = "Demand of " + FFErep + " from " + demand.getOrigin().getUNLocode() + " to " + 
				demand.getDestination().getUNLocode() + " uses route: \n";
		int counter = 1;
		boolean wasDwell = false;
		str += "Leg " + counter + ": ";
		for(Edge e : route){
			if(e.isSail() && !wasDwell){
				str += e.getFromPortUNLo() + "-" + e.getToPortUNLo();
			} else if(e.isSail() && wasDwell){
				str += "-" + e.getToPortUNLo();
				wasDwell = false;
			} else if(e.isDwell()){
				wasDwell = true;
			} else if(e.isTransshipment()){
				wasDwell = false;
				counter++;
				str += "\nLeg " + counter + ": ";
			} else if(e.isOmission()){
				str += e.getFromPortUNLo() + "-" + e.getToPortUNLo() + " via omission edge";
			} 
			counter++;
		}
		str += "\n";
		return str;
	}
}
