import static java.util.stream.Collectors.joining;
import static java.util.stream.IntStream.range;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MonitoredTaskRunnerTool {

  public static void main(String[] args) {
    TaskExecutor taskExecutor = new TaskExecutor();
    System.err.println("\n" + taskExecutor.run().orElse("no response"));
  }
}

class TaskExecutor {

  private final Task task;
  private final Monitor monitor;

  private final ExecutorService executorService;

  public TaskExecutor() {
    final BlockingQueue<Event> eventBus = new LinkedBlockingQueue<>(1);
    this.task = new Task(eventBus);
    this.monitor = new Monitor(eventBus);
    this.executorService = Executors.newFixedThreadPool(2);
  }

  public Optional<String> run() {
    try {
      executorService.execute(monitor);
      Future<String> submit = executorService.submit(task);
      return Optional.of(submit.get(10, TimeUnit.MINUTES));
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      e.printStackTrace();
      return Optional.empty();
    } finally {
      executorService.shutdown();
      try {
        Thread.sleep(2000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      executorService.shutdownNow();
    }
  }
}

class Task implements Callable<String> {

  private BlockingQueue<? super Event> eventBus;

  public Task(BlockingQueue<? super Event> eventBus) {
    this.eventBus = eventBus;
  }

  @Override
  public String call() throws Exception {
    eventBus.put(new Event(0));
    for (int i = 0; i < 100; i = i + ThreadLocalRandom.current().nextInt(1,5)) {
      int sleepMillis = ThreadLocalRandom.current().nextInt(200, 1000) ;
      Thread.sleep(sleepMillis);
      eventBus.put(new Event(i));
    }
    eventBus.put(new Event(100));
    return "some result";
  }
}

class Monitor implements Runnable {

  private static final String PROGRESS_LINE_TEMPLATE = "[%s] %d%%";
  private static final int PROGRESS_DOT_LENGTH = 25;

  private BlockingQueue<? extends Event> eventBus;

  public Monitor(BlockingQueue<? extends Event> eventBus) {
    this.eventBus = eventBus;
  }

  @Override
  public void run() {
    try {
      boolean isJobDone = false;
      while(!isJobDone) {
        Event event = eventBus.take();
        System.err.print("\r");
        System.err.print(createProgressLine(event.getProgress()));
        if (event.getProgress() >= 100) {
          isJobDone = true;
        }
      }
    } catch (InterruptedException e) {
//      e.printStackTrace();
    }
  }

  private String createProgressLine(int progress) {
    int jobDone = (int) ((progress / 100.0) * PROGRESS_DOT_LENGTH);
    return String.format(PROGRESS_LINE_TEMPLATE, generateProgressString(jobDone), progress);
  }

  private String generateProgressString(int jobDone) {
    return range(0, jobDone).mapToObj(v -> ".").collect(joining()) +
        range(jobDone, PROGRESS_DOT_LENGTH).mapToObj(v -> " ").collect(joining());
  }
}

class Event {

  private final int progress;

  public Event(int progress) {
    this.progress = progress;
  }

  public int getProgress() {
    return progress;
  }
}