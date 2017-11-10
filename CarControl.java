// Prototype implementation of Car Control
// Mandatory assignment
// Course 02158 Concurrent Programming, DTU, Fall 2017

// Hans Henrik Lovengreen     Oct 9, 2017

import java.awt.Color;
import java.util.*;
import java.util.concurrent.locks.*;

class Gate {

    Semaphore g = new Semaphore(0);
    Semaphore e = new Semaphore(1);
    boolean isopen = false;

    public void pass() throws InterruptedException {
        g.P();
        g.V();
    }

    public void open() {
        try {
            e.P();
        } catch (InterruptedException e) {
        }
        if (!isopen) {
            g.V();
            isopen = true;
        }
        e.V();
    }

    public void close() {
        try {
            e.P();
        } catch (InterruptedException e) {
        }
        if (isopen) {
            try {
                g.P();
            } catch (InterruptedException e) {
            }
            isopen = false;
        }
        e.V();
    }
}

class Car extends Thread {

    int basespeed = 100; // Rather: degree of slowness
    int variation = 50;  // Percentage of base speed

    CarDisplayI cd; // GUI part

    int no;       // Car number
    Pos startpos; // Startpositon (provided by GUI)
    Pos barpos;   // Barrierpositon (provided by GUI)
    Color col;    // Car  color
    Gate mygate;  // Gate at startposition

    int speed;  // Current car speed
    Pos curpos; // Current position
    Pos newpos; // New position to go to

    // SOFYA'S CODE

    // static alley object
    static Alley alley = new Alley();
    // customizable position for alley entrance
    Pos alleyenter;
    // customizable position for alley exit
    Pos alleyleave;

    // static map of positions to their semaphores
    static CurPosMap curMap = new CurPosMap();

    // barrier for all cars
    Barrier barrier;
    // customizable position for barrier stop
    Pos barrierstop;

    // END

    // SOFYA'S CODE -- added barrier to parameters
    public Car(int no, CarDisplayI cd, Gate g, Barrier barrier) {

        this.no = no;
        this.barrier = barrier;

        // SOFYA'S CODE

        // customizing the alley entrance and exit positions
        switch (no) {
        case 0:
            barrierstop = new Pos(5, 3);
            break;
        case 1:
        case 2:
            alleyenter = new Pos(2, 0);
            alleyleave = new Pos(9, 1);
            barrierstop = new Pos(5, 0);
            break;
        case 3:
        case 4:
            alleyenter = new Pos(1, 2);
            alleyleave = new Pos(9, 1);
            barrierstop = new Pos(5, 0);
            break;
        default:
            alleyenter = new Pos(10, 0);
            alleyleave = new Pos(0, 2);
            barrierstop = new Pos(6, 0);
            break;
        }

        // END

        this.cd = cd;
        mygate = g;
        startpos = cd.getStartPos(no);
        barpos = cd.getBarrierPos(no); // For later use

        col = chooseColor();

        // do not change the special settings for car no. 0
        if (no == 0) {
            basespeed = 0;
            variation = 0;
            setPriority(Thread.MAX_PRIORITY);
        }
    }

    public synchronized void setSpeed(int speed) {
        if (no != 0 && speed >= 0) {
            basespeed = speed;
        } else
            cd.println("Illegal speed settings");
    }

    public synchronized void setVariation(int var) {
        if (no != 0 && 0 <= var && var <= 100) {
            variation = var;
        } else
            cd.println("Illegal variation settings");
    }

    synchronized int chooseSpeed() {
        double factor = (1.0D + (Math.random() - 0.5D) * 2 * variation / 100);
        return (int)Math.round(factor * basespeed);
    }

    private int speed() {
        // Slow down if requested
        final int slowfactor = 3;
        return speed * (cd.isSlow(curpos) ? slowfactor : 1);
    }

    Color chooseColor() {
        return Color.blue; // You can get any color, as longs as it's blue
    }

    Pos nextPos(Pos pos) {
        // Get my track from display
        return cd.nextPos(no, pos);
    }

    Pos curPos() { return curpos; }

    boolean atGate(Pos pos) { return pos.equals(startpos); }

    public void run() {
        try {

            speed = chooseSpeed();

            // SOFYA'S CODE
            // lock the semaphore for the starting position of the car
            curMap.get(startpos).P();
            // END

            curpos = startpos;
            cd.mark(curpos, col, no);

            while (true) {
                sleep(speed());

                if (atGate(curpos)) {
                    mygate.pass();
                    speed = chooseSpeed();
                }

                newpos = nextPos(curpos);

                // SOFYA'S CODE

                // if the next position is the entrance to alley, enter
                // else if the exit to alley, exit
                if (newpos.equals(alleyenter)) {
                    alley.enter(this.no);
                } else if (newpos.equals(alleyleave)) {
                    alley.leave(this.no);
                }
                // if the next position is past the barrier, sync
                if (this.no != 0) {
                    if (newpos.row == barrierstop.row && newpos.col != 0) {
                        barrier.sync();
                    }
                } else {
                    if (newpos.equals(barrierstop)) {
                        barrier.sync();
                    }
                }

                // lock the next position
                curMap.get(newpos).P();

                // END

                //  Move to new position
                cd.clear(curpos);
                cd.mark(curpos, newpos, col, no);
                sleep(speed());

                // SOFYA'S CODE
                // release the previous position
                curMap.get(curpos).V();
                // END

                cd.clear(curpos, newpos);
                cd.mark(newpos, col, no);

                curpos = newpos;
            }

        } catch (Exception e) {
            cd.println("Exception in Car no. " + no);
            System.err.println("Exception in Car no. " + no + ":" + e);
            e.printStackTrace();
        }
    }
}

class Alley {
    private final Lock mut = new ReentrantLock();
    private final Condition access = mut.newCondition();
    private boolean goingUp;
    private boolean goingDown;
    private int count; // Number of vehicles in alley

    /* Variables used for fairness */
    public boolean isFair;
    private int[] laps; // How many times alley used in each direction
    private static final int DOWN = 0; // label array indices
    private static final int UP = 1;

    public Alley() {
        goingUp = goingDown = false;
        laps = new int[2];
        count = 0;
        isFair = false; // Default to unfair
    }

    public Alley(boolean isFair) {
        goingUp = goingDown = false;
        laps = new int[2];
        count = 0;
        this.isFair = isFair;
    }

    private boolean canEnter(int no) {
        if (no <= 4)
            return !goingUp; // cars 1-4 go down only
        else
            return !goingDown;
    }

    /**
     * If true, the car will wait out of fairness for other drivers
     *
     * Will let all cars going in one direction finish before allowing
     * cars in the other direction.
     */
    private boolean beFairAndWait(int no) {
        if (no <= 4)
            return isFair && laps[DOWN] % 4 == 0 && laps[DOWN] > laps[UP];
        else
            return isFair && laps[UP] % 4 == 0 && laps[UP] > laps[DOWN];
    }

    private void radioIn(int no) {
        if (no <= 4) {
            goingDown = true;
            laps[DOWN]++;
        } else {
            goingUp = true;
            laps[UP]++;
        }
        count++;
        access.signal();
    }

    private void radioOut(int no) {
        if (--count == 0)
            // Last car sends signal to those waiting that it's okay to enter
            goingUp = goingDown = false;
        access.signal();
    }

    public void enter(int no) throws InterruptedException {
        mut.lock();

        try {
            while (beFairAndWait(no) || !canEnter(no))
                access.await();
            radioIn(no);
        } finally {
            mut.unlock();
        }
    }

    public void leave(int no) throws InterruptedException {
        mut.lock();

        try {
            radioOut(no);
        } finally {
            mut.unlock();
        }
    }
}

class Barrier {
    private final Lock mut = new ReentrantLock();
    private final Condition cv = mut.newCondition();

    private int numCars; // Total number of cars
    private int count;
    private int numLeft; // Number of cars that have yet to reach the barrier

    private boolean active;

    public Barrier() {
        numLeft = count = numCars = 8; // default, without no. 0
        active = false;
    }

    public Barrier(int numCars) {
        numLeft = count = this.numCars = numCars;
        active = false;
    }

    /*! IMPORTANT: Must be called in critical section */
    public void reset() { numLeft = numCars; }

    public void sync() throws InterruptedException {
        mut.lock();
        if (active) {
            try {
                // Have we seen all cars?
                if (--numLeft == 0) {
                    // Yes. Increment the counter to tell current waiters
                    // that it is their turn
                    ++count;
                    cv.signal();
                } else {
                    int localCount = count;

                    // Wait for count to change or the barrier to turn off
                    while (localCount == count && active)
                        cv.await();
                }
                cv.signal();

                // Reset if this was the last car or inactive
                if (numLeft == 0 || !active)
                    reset();

            } finally {
            }
        }
        mut.unlock();
    }

    public void on() { active = true; }

    public void off() {
        mut.lock();
        active = false;
        cv.signal(); // Notify drivers that barrier is off
        mut.unlock();
    }

    public boolean isOn() { return active; }

    public void setOn() { active = true; }

    public void setNumcars(int n) { this.numCars = n; }
}

class CurPosMap {

    // map from positions to their semaphores
    private HashMap<Pos, Semaphore> map;

    public CurPosMap() {
        map = new HashMap<Pos, Semaphore>();
        // defaulting to semaphores of 1
        for (int i = 0; i < 11; i++) {
            for (int j = 0; j < 12; j++) {
                map.put(new Pos(i, j), new Semaphore(1));
            }
        }
    }

    // getter for a semaphore
    public Semaphore get(Pos position) { return map.get(position); }
}
// END

public class CarControl implements CarControlI {

    CarDisplayI cd; // Reference to GUI
    Car[] car;      // Cars
    Gate[] gate;    // Gates
    Barrier barrier;

    public CarControl(CarDisplayI cd) {
        this.cd = cd;
        car = new Car[9];
        gate = new Gate[9];
        barrier = new Barrier();

        for (int no = 0; no < 9; no++) {
            gate[no] = new Gate();
            // SOFYA'S CODE -- added barrier to parameters
            car[no] = new Car(no, cd, gate[no], barrier);
            car[no].start();
        }
    }

    public void startCar(int no) {
        // SOFYA'S CODE
        // in case the cars were stopped and then started again,
        // but the barrier is still supposed to be on
        if (no > 0) {
            if (barrier.isOn()) {
                barrier.on();
            }
        }
        // END
        gate[no].open();

        // SOFYA'S CODE
        // so that the barrier knows how many cars to expect
        if (no == 0) {
            barrier.setNumcars(9);
        }
    }

    public void stopCar(int no) {
        // SOFYA'S CODE
        /*
        // ?????
        if (no > 0) {
            if (barrier.isOn()) {
                barrier.off();
                barrier.setOn();
            }
        }
        */
        // END

        gate[no].close();

        // SOFYA'S CODE
        // so that the barrier knows how many cars to expect
        if (no == 0) {
            barrier.setNumcars(8);
        }
        // END
    }

    public void barrierOn() { barrier.on(); }

    public void barrierOff() { barrier.off(); }

    public void barrierSwitch() {
        if (barrier.isOn())
            barrier.off();
        else
            barrier.on();
    }

    public void barrierShutDown() {
        cd.println("Barrier shut down not implemented in this version");
        // This sleep is for illustrating how blocking affects the GUI
        // Remove when shutdown is implemented.
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
        }
        // Recommendation:
        //   If not implemented call barrier.off() instead to make graphics
        //   consistent
    }

    public void setLimit(int k) {
        cd.println("Setting of bridge limit not implemented in this version");
    }

    public void removeCar(int no) {
        cd.println("Remove Car not implemented in this version");
    }

    public void restoreCar(int no) {
        cd.println("Restore Car not implemented in this version");
    }

    /* Speed settings for testing purposes */

    public void setSpeed(int no, int speed) { car[no].setSpeed(speed); }

    public void setVariation(int no, int var) { car[no].setVariation(var); }
}
