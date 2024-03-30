#include <iostream>
#include <vector>
#include <chrono>
#include <ctime>
#include <thread>
#include <sysinfo/sysinfo.hpp>
#include <amqpcpp.h>

struct SystemInfo {
    double total_cpu;
    double used_cpu;
    uint64_t used_memory;
    uint64_t total_memory;
    uint64_t timestamp;
};

std::pair<double, double> calculate_cpu_amount(const std::vector<sysinfo::Processor>& processors) {
    double total_cpu = 0.0;
    double used_cpu = 0.0;
    for (const auto& processor : processors) {
        total_cpu += static_cast<double>(processor.getFrequency());
        used_cpu += static_cast<double>(processor.getUsage());
    }
    return {total_cpu, used_cpu};
}

SystemInfo get_system_info() {
    sysinfo::System system;
    system.refreshAll();

    uint64_t total_memory = system.getTotalRam();
    uint64_t used_memory = system.getUsedRam();
    auto processors = system.getProcessors();
    auto [total_cpu, used_cpu] = calculate_cpu_amount(processors);

    return {
        total_cpu,
        used_cpu,
        used_memory,
        total_memory,
        static_cast<uint64_t>(std::chrono::system_clock::to_time_t(std::chrono::system_clock::now()))
    };
}

void send_to_rabbitmq(const SystemInfo& system_info) {
    const char* amqp_addr = std::getenv("AMQP_ADDR");
    AMQP::Address address(amqp_addr);

    AMQP::TcpConnection connection(&address);
    AMQP::TcpChannel channel(&connection);
    
    const std::string queue_name = "system_info_queue";
    channel.declareQueue(queue_name);
    
    std::string message = "{\"total_cpu\":" + std::to_string(system_info.total_cpu) +
                          ",\"used_cpu\":" + std::to_string(system_info.used_cpu) +
                          ",\"used_memory\":" + std::to_string(system_info.used_memory) +
                          ",\"total_memory\":" + std::to_string(system_info.total_memory) +
                          ",\"timestamp\":" + std::to_string(system_info.timestamp) +
                          "}";
    
    channel.publish("", queue_name, message);
    std::cout << "Sent: " << message << std::endl;
}

int main() {
    while (true) {
        SystemInfo system_info = get_system_info();
        send_to_rabbitmq(system_info);
        std::this_thread::sleep_for(std::chrono::seconds(3));
    }
    return 0;
}
