import sysinfo
import asyncio
import aio_pika
import json
import os
import time

async def calculate_cpu_amount(processors):
    total_cpu = sum(float(p.get_frequency()) for p in processors)
    used_cpu = sum(float(p.get_cpu_usage()) for p in processors)
    return total_cpu, used_cpu

def get_system_info():
    system = sysinfo.SysInfo()
    total_memory = system.totalram
    used_memory = system.usedram
    processors = system.cpus
    total_cpu, used_cpu = calculate_cpu_amount(processors)

    return {
        "total_cpu": total_cpu,
        "used_cpu": used_cpu,
        "used_memory": used_memory,
        "total_memory": total_memory,
        "timestamp": int(time.time())
    }

async def send_to_rabbitmq(message):
    amqp_addr = os.getenv("AMQP_ADDR")
    connection = await aio_pika.connect_robust(amqp_addr)
    
    async with connection:
        channel = await connection.channel()
        queue_name = "system_info_queue"
        
        queue = await channel.declare_queue(queue_name, durable=True)
        await channel.default_exchange.publish(
            aio_pika.Message(body=json.dumps(message).encode()),
            routing_key=queue_name
        )
        print(f"Sent: {message}")

async def main():
    while True:
        system_info = get_system_info()
        await send_to_rabbitmq(system_info)
        await asyncio.sleep(3)

if __name__ == "__main__":
    asyncio.run(main())
