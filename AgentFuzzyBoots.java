package client.agents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.PriorityQueue;

import server.Action;
import server.Directions;

public class AgentFuzzyBoots extends AbstractAgent {
	private static final int AcceptableDangerEstimate = 25;
	private static final int AcceptableShootEstimate = 50;
	private static final int AcceptablePitEstimate = 50;
	/**
	 * {@link AgentFuzzyBoots} is an agent with an internal model of the world
	 * and goals it tries to accomplish. Main goals: 
	 * 1. Collect gold 
	 * 2. Shoot nearby Wumpus
	 * 3. Explore 
	 * 4. Return Home
	 * The goals are checked in this priority. So if it is possible to collect gold, 
	 * the agent will always collect gold.
	 * If the agent found a goal he wants to use, he creates a plan how to accomplish
	 * the goal.
	 * 
	 * Pathfinding is done by Dijkstra Algorithm.
	 *
	 */
	
	private int currentOrientation;
	private InternalCell currentCell;
	private int arrowCount;

	/**
	 * The agents view of the world
	 */
	private HashMap<Position, InternalCell> map;

	/**
	 * Current sequence the agent is using
	 */
	private List<Integer> sequence = null;

	private Goal[] goals = { new PickupGoldGoal(), new ShootWumpusGoal(),
			new ExploreGoal(), new ReturnHomeGoal() };
	//private Goal[] goals = { new PickupGoldGoal(),
	//				new ExploreGoal(), new ReturnHomeGoal() };
			
	@Override
	protected int chooseAction(Percepts p) {
		updateCells(p);
		if (sequence == null || sequence.isEmpty()) {
			Goal goal = generateGoal();
			sequence = goal.generateActionSequence(this);
		}
		int action = sequence.remove(0);

		// Make sure we set the current rotation correctly
		if (action == Action.TURN_RIGHT) {
			currentOrientation = Directions.turnRight(currentOrientation);
		} else if (action == Action.TURN_LEFT) {
			currentOrientation = Directions.turnLeft(currentOrientation);
		}
		return action;
	}

	private Goal generateGoal() {
		for (Goal goal : goals) {
			if (goal.choosable(this))
				return goal;
		}
		throw new RuntimeException("Bad things are bad");
	}

	private void updateCells(Percepts p) {
		arrowCount = p.numArrows;
		InternalCell next = currentCell;
		if (p.lastAction == Action.GOFORWARD) {
			next = getNextCell();
		} else if (p.lastAction == Action.SHOOT) {
			if (p.scream && ! p.stench) {
				InternalCell target = getNextCell();
				for (Position neighbor: Position.getSurroundingPositions(target.position)) {
					InternalCell cell = map.get(neighbor);
					if (cell != null) cell.hasStench = false;
				}
			}
		}
		if (!p.bump) {
			currentCell = next;
			addSurroundingCells();
			next.loadPercept(p);
		} else {
			System.out.println("OUH Wall..");
			this.sequence = null;
			next.isWall = true;
		}
		next.visited = true;
		System.out.println("Now at: " + currentCell.position.x + ":"
				+ currentCell.position.y);
	}

	private InternalCell getNextCell() {
		int x_coord = currentCell.position.x;
		int y_coord = currentCell.position.y;
		switch (currentOrientation) {
		case Directions.EAST:
			++x_coord;
			break;
		case Directions.WEST:
			--x_coord;
			break;
		case Directions.SOUTH:
			y_coord++;
			break;
		case Directions.NORTH:
			y_coord--;
			break;
		}
		return map.get(new Position(x_coord, y_coord));
	}

	private void addSurroundingCells() {
		for (Position pos : Position
				.getSurroundingPositions(currentCell.position)) {
			if (map.get(pos) == null)
				map.put(pos, new InternalCell(pos));
		}
	}

	@Override
	public void resetAgent() {
		map = new HashMap<Position, InternalCell>();		
		this.currentOrientation = this.startOrientation;
		InternalCell start = new InternalCell(new Position(this.startLocationX,
		this.startLocationY));
		this.currentCell = start;
		start.visited = true;
		map.put(currentCell.position, start);
		addSurroundingCells();
	}

	public static List<Integer> getActionSequenceForPath(
			List<InternalCell> path, AgentFuzzyBoots agent) {
		ArrayList<Integer> ret = new ArrayList<Integer>();
		InternalCell last = agent.currentCell;
		int orientation = agent.currentOrientation;
		int toDirection = Directions.EAST;
		int turnsleft, turnsright;
		for (InternalCell cur : path) {
			if (cur.position.x < last.position.x) {
				toDirection = Directions.WEST;
			} else if (cur.position.x > last.position.x) {
				toDirection = Directions.EAST;
			} else if (cur.position.y < last.position.y) {
				toDirection = Directions.NORTH;
			} else if (cur.position.y > last.position.y) {
				toDirection = Directions.SOUTH;
			}
			turnsleft = Directions.getRequiredLeftTurns(orientation, toDirection);
			turnsright = Directions.getRequiredRightTurns(orientation, toDirection);
			if(turnsleft < turnsright) {
				for (int i = 0; i < turnsleft % 4; ++i) {
					ret.add(Action.TURN_LEFT);
				}
			} else {
				for (int i = 0; i < turnsright % 4; ++i) {
					ret.add(Action.TURN_RIGHT);
				}
			}
			orientation = toDirection;
			ret.add(Action.GOFORWARD);
			last = cur;
		}
		return ret;
	}

	private static class InternalCell {
		public final Position position;
		public boolean hasGold = false;
		public boolean hasStench = false;
		public boolean hasBreeze = false;
		public boolean isWall = false;
		public boolean visited = false;

		public InternalCell(Position pos) {
			position = pos;
			if (pos.x < 0 || pos.y < 0 || pos.x > 9 || pos.y > 9)
				isWall = true;
		}

		/**
		 * Returns how dangerous this field is.
		 * 
		 * @return
		 */
		public int getDangerEstimate(AgentFuzzyBoots agent) {
			// System.out.println("Danger Estimate " + toString() + ": " +
			// (hasWumpus(agent) + hasPit(agent)));
			return (hasWumpus(agent) + hasPit(agent));
		}

		public String toString() {
			return "InternalCell[" + position.x + ":" + position.y + "]";
		}

		public int hasWumpus(AgentFuzzyBoots agent) {
			if (visited)
				return 0;
			int chance = 0;
			for (Position pos : Position.getSurroundingPositions(position)) {
				if (agent.map.get(pos) != null) {
					if (agent.map.get(pos).hasStench) {
						chance += 25;
					} else if (agent.map.get(pos).visited
							&& !agent.map.get(pos).hasStench
							&& !agent.map.get(pos).isWall) {
						return 0;
					}
				}
			}
			// System.out.println("Wumpus danger for " + this + ": " + chance);
			return chance;
		}

		public int hasPit(AgentFuzzyBoots agent) {
			if (visited)
				return 0;
			int chance = 0;
			for (Position pos : Position.getSurroundingPositions(position)) {
				if (agent.map.get(pos) != null) {
					if (agent.map.get(pos).hasBreeze) {
						chance += 25;
					} else if (agent.map.get(pos).visited
							&& !agent.map.get(pos).hasBreeze
							&& !agent.map.get(pos).isWall) {
						return 0;
					}
				}
			}
			// System.out.println("Pitdanger for " + this + ": " + chance);
			return chance;
		}

		public void loadPercept(Percepts perc) {
			hasStench = perc.stench;
			hasBreeze = perc.breeze;
			hasGold = perc.glitter;
		}
	}

	static class Position implements Comparable<Position> {

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + x;
			result = prime * result + y;
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Position other = (Position) obj;
			if (x != other.x)
				return false;
			if (y != other.y)
				return false;
			return true;
		}

		public final int x, y;

		public Position(int x, int y) {
			this.x = x;
			this.y = y;
		}

		@Override
		public int compareTo(Position o) {
			return (o.x == this.x && o.y == this.y) ? 0 : 1;
		}

		/**
		 * Returns the {@link Position} Objects for all Positions around the specified position center.
		 * @param center The Center around which the {@link Position}s are to be collected. 
		 * @return {@link Position} Objects for all Positions around the specified position.
		 */
		public static ArrayList<AgentFuzzyBoots.Position> getSurroundingPositions(
				AgentFuzzyBoots.Position center) {
			ArrayList<AgentFuzzyBoots.Position> positions = new ArrayList<AgentFuzzyBoots.Position>(
					4);
			positions.add(new AgentFuzzyBoots.Position(center.x + 1, center.y));
			positions.add(new AgentFuzzyBoots.Position(center.x - 1, center.y));
			positions.add(new AgentFuzzyBoots.Position(center.x, center.y + 1));
			positions.add(new AgentFuzzyBoots.Position(center.x, center.y - 1));
			return positions;
		}
	}
	
	// ==============================================
	//              Goals start here
	// ==============================================

	private static interface Goal {
		/**
		 * Check whether this {@link Goal} is choosable for execution.
		 */
		public boolean choosable(AgentFuzzyBoots agent);
		/**
		 * Generate a list of actions to accomplish this goal.
		 * @return {@link List} of actions.
		 */
		public List<Integer> generateActionSequence(AgentFuzzyBoots agent);
	}

	private static class ExploreGoal implements Goal {

		private List<Integer> actions;

		@Override
		public boolean choosable(final AgentFuzzyBoots agent) {
			// We can still try to find gold if we have a cell on our map
			// we haven't visited and that isn't too dangerous
			final List<List<Integer>> actionlist = new ArrayList<List<Integer>>();
			List<InternalCell> path = new ArrayList<InternalCell>();
			for (InternalCell next: agent.map.values()) {
				if (!next.visited && next.getDangerEstimate(agent) <= AcceptableDangerEstimate) {
					path = calculateRoute(agent.currentCell, next, agent);
					if(path != null) {
						actionlist.add(getActionSequenceForPath(path, agent));
					}
				}
				
			}
			if(actionlist.isEmpty()) return false;
			actions = Collections.min(actionlist, new ActionListComparator());
			return actions != null;
		}

		@Override
		public List<Integer> generateActionSequence(AgentFuzzyBoots agent) {
			return actions;
		}

	}

	private static class ShootWumpusGoal implements Goal {

		private List<InternalCell> path;
		private List<Integer> actions;

		@Override
		public boolean choosable(final AgentFuzzyBoots agent) {
			if (agent.arrowCount < 1) return false;
			final List<List<Integer>> actionlist = new ArrayList<List<Integer>>();
			List<InternalCell> path = new ArrayList<InternalCell>();
			for (InternalCell next: agent.map.values()) {
				if (!next.visited && next.hasWumpus(agent) >= AcceptableShootEstimate &&
						next.hasPit(agent) < AcceptablePitEstimate) {
					for(Position neigh: Position.getSurroundingPositions(next.position)) {
						path = calculateRoute(agent.currentCell, agent.map.get(neigh), agent);
						if(path != null) {
							path.add(next);
							actionlist.add(getActionSequenceForPath(path, agent));
						}
					}
				}
				
			}
			if(actionlist.isEmpty()) return false;
			actions = Collections.min(actionlist, new ActionListComparator());
			return actions != null;
		}

		@Override
		public List<Integer> generateActionSequence(AgentFuzzyBoots agent) {
			actions.remove(actions.size()-1);
			actions.add(Action.SHOOT);
			return actions;
		}

	}

	private static class ActionListComparator implements Comparator<List<Integer>> {
		@Override
		public int compare(List<Integer> actions1, List<Integer> actions2) {
			if(actions1 == null) return 1;
			else if (actions2 == null) return -1;
			else return Integer.valueOf(actions1.size()).compareTo(actions2.size());
		}
	}
	
	private static class ReturnHomeGoal implements Goal {

		@Override
		public boolean choosable(AgentFuzzyBoots agent) {
			return true;// always return true, we can always go home
		}

		@Override
		public List<Integer> generateActionSequence(AgentFuzzyBoots agent) {
			ArrayList<InternalCell> path = calculateRoute(agent.currentCell,
					agent.map.get(new Position(agent.startLocationX,
							agent.startLocationY)), agent);
			List<Integer> sequence = getActionSequenceForPath(path, agent);
			sequence.add(Action.CLIMB);
			return sequence;
		}
	}

	private static class PickupGoldGoal implements Goal {

		@Override
		public boolean choosable(AgentFuzzyBoots agent) {
			return agent.currentCell.hasGold;
		}

		@Override
		public ArrayList<Integer> generateActionSequence(AgentFuzzyBoots agent) {
			ArrayList<Integer> ret = new ArrayList<Integer>(1);
			ret.add(Action.GRAB);
			return ret;
		}
	}

	/**
	 * Calculate a route from from to to. :) Uses Dijkstra's pathfinding algorithm.
	 * @param from {@link InternalCell} on which to start.
	 * @param to {@link InternalCell} we want to find a path to.
	 * @param agent The {@link AgentFuzzyBoots} that will walk this path.
	 * @return <code>null</code> if no path is found, otherwise {@link List} of {@link InternalCell}s for the path.
	 */
	public static ArrayList<InternalCell> calculateRoute(InternalCell from,
			InternalCell to, AgentFuzzyBoots agent) {
		// System.out.printf("Looking for path from %s to %s\n",
		// from.toString(), to.toString());
		ArrayList<InternalCell> ret = new ArrayList<InternalCell>();
		final HashMap<InternalCell, Integer> distance = new HashMap<InternalCell, Integer>();
		HashMap<InternalCell, InternalCell> previous = new HashMap<InternalCell, InternalCell>();

		// Priority queue for dijkstra
		PriorityQueue<InternalCell> que = new PriorityQueue<InternalCell>(
				agent.map.size(), new Comparator<InternalCell>() {
					public int compare(InternalCell o1, InternalCell o2) {
						if (distance.get(o1) == null)
							return 1;
						else if (distance.get(o2) == null)
							return -1;
						else
							return distance.get(o1).compareTo(distance.get(o2));
					}
				});
		for (InternalCell entry : agent.map.values()) {
			if (entry.getDangerEstimate(agent) < AcceptableDangerEstimate
					&& !entry.isWall) {
				distance.put(entry, null);
				previous.put(entry, null);
				que.add(entry);
			}
		}
		que.remove(from);
		distance.put(from, 0);
		que.add(from);
		ArrayList<InternalCell> neighbors = new ArrayList<InternalCell>();
		InternalCell u = null;
		while (!que.isEmpty()) {
			u = que.peek();
			if (distance.get(u) == null)
				break;
			que.poll();
			neighbors.clear();
			for (InternalCell entry : que) {
				if (Math.abs(entry.position.x - u.position.x)
						+ Math.abs(entry.position.y - u.position.y) == 1) {
					neighbors.add(entry);
				}
			}
			for (InternalCell neigh : neighbors) {
				int gain = neigh.visited ? 1 : 0;
				int alt = distance.get(u) + gain;
				if (distance.get(neigh) == null || alt < distance.get(neigh)) {
					que.remove(neigh);
					distance.put(neigh, alt);
					previous.put(neigh, u);
					que.add(neigh);
				}
			}
		}
		InternalCell tmp = to;
		while (tmp != from) {
			if (tmp == null)
				return null; // no way found
			ret.add(tmp);
			tmp = previous.get(tmp);
		}
		Collections.reverse(ret);
		System.out.printf("Moving from %d:%d to %d:%d distance: %d\n",
				from.position.x, from.position.y, to.position.x, to.position.y,
				distance.get(to));
		System.out.print("Path: ");
		for (InternalCell cell : ret) {
			System.out.printf("[%d:%d] -> ", cell.position.x, cell.position.y);
		}
		System.out.println("Target Reached");
		return ret;
	}

}
