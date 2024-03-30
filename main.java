import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.TimeUnit;
import com.sysinfo.SysInfo;
import com.sysinfo.SysInfoException;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.QueueingConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;

class SystemInfo {
    double total_cpu;
    double used_cpu;
    long used_memory;
    long total_memory;
    long timestamp;
}

public class Main {
    private static final String QUEUE_NAME = "system_info_queue";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static List<SysInfo.Processor> getProcessors() throws SysInfoException {
        SysInfo sysInfo = new SysInfo();
        return sysInfo.getProcessors();
    }

    private static double[] calculateCpuAmount(List<SysInfo.Processor> processors) {
        double totalCpu = 0.0;
        double usedCpu = 0.0;
        for (SysInfo.Processor processor : processors) {
            totalCpu += processor.getFrequency();
            usedCpu += processor.getUsage();
        }
        return new double[]{totalCpu, usedCpu};
    }

    private static SystemInfo getSystemInfo() throws SysInfoException {
        SysInfo sysInfo = new SysInfo();
        sysInfo.refreshAll();

        List<SysInfo.Processor> processors = getProcessors();
        double[] cpuAmount = calculateCpuAmount(processors);

        SystemInfo systemInfo = new SystemInfo();
        systemInfo.total_cpu = cpuAmount[0];
        systemInfo.used_cpu = cpuAmount[1];
        systemInfo.used_memory = sysInfo.getUsedRam();
        systemInfo.total_memory = sysInfo.getTotalRam();
        systemInfo.timestamp = new Date().getTime() / 1000;

        return systemInfo;
    }

    private static void sendToRabbitMQ(SystemInfo systemInfo) throws Exception {
        String AMQP_ADDR = System.getenv("AMQP_ADDR");

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(AMQP_ADDR);
        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            channel.queueDeclare(QUEUE_NAME, false, false, false, null);
            String message = objectMapper.writeValueAsString(systemInfo);
            channel.basicPublish("", QUEUE_NAME, null, message.getBytes());
            System.out.println("Sent: " + message);
        }
    }

    public static void main(String[] args) throws Exception {
        while (true) {
            SystemInfo systemInfo = getSystemInfo();
            sendToRabbitMQ(systemInfo);
            TimeUnit.SECONDS.sleep(3);
        }
    }
}
