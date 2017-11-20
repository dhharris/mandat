// Prototype implementation of Car Test class
// Mandatory assignment
// Course 02158 Concurrent Programming, DTU, Fall 2017

// Hans Henrik Lovengreen     Oct 9, 2017
import java.util.concurrent.ThreadLocalRandom;

public class CarTest extends Thread {

    CarTestingI cars;
    int testno;
    private static final int TEST_TIME = 15000; // Test time in ms

    public CarTest(CarTestingI ct, int no) {
        cars = ct;
        testno = no;
    }

    public void run() {
        int sleepTime;
        try {
            switch (testno) {
            case 0:
                // Demonstration of startAll/stopAll.
                cars.startAll();
                sleep(3000);
                cars.stopAll();
                break;

            case 1:
                // Demonstration of Barrier
                // Should be enough time for one barrier wait
                cars.startAll();
                cars.barrierOn();
                sleep(TEST_TIME);
                cars.stopAll();
                break;

            case 2:
                // Demonstration of Barrier going on and off randomly
                sleepTime = 0;
                cars.startAll();
                while (sleepTime < TEST_TIME) {
                    int rand = ThreadLocalRandom.current().nextInt(2000, 3001);
                    sleep(rand);
                    cars.barrierSwitch();
                    sleepTime += rand;
                }
                cars.stopAll();
                break;

            case 3:
                // Demonstration of car 5 being serviced 
                cars.startAll();
                sleep(TEST_TIME / 3);

                cars.println("Removing car 5");
                cars.removeCar(5);

                sleep(TEST_TIME / 3);

                cars.println("Restoring car 5");
                cars.restoreCar(5);

                sleep(TEST_TIME / 3);
                cars.stopAll();
                break;

            case 4:
                // Demonstration of car being removed at random times
                sleepTime = 0;
                boolean isRemoved = false;
                cars.startAll();
                while (sleepTime < TEST_TIME) {
                    int rand = ThreadLocalRandom.current().nextInt(2000, 3001);
                    sleep(rand);
                    if (!isRemoved) {
                        cars.println("Removing car 5");
                        cars.removeCar(5);
                        isRemoved = true;
                    } else {
                        cars.println("Restoring car 5");
                        cars.restoreCar(5);
                        isRemoved = false;
                    }
                    sleepTime += rand;
                }
                cars.stopAll();
                break;

            case 19:
                // Demonstration of speed setting.
                // Change speed to double of default values
                cars.println("Doubling speeds");
                for (int i = 1; i < 9; i++) {
                    cars.setSpeed(i, 50);
                };
                break;

            default:
                cars.println("Test " + testno + " not available");
            }

            cars.println("Test ended");

        } catch (Exception e) {
            System.err.println("Exception in test: " + e);
        }
    }
}
