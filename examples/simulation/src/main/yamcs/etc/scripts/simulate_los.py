import argparse
import os
import time

from yamcs.client import YamcsClient


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "-d",
        "--duration",
        help="LOS duration in seconds",
        type=float,
        default=60,
    )
    args = parser.parse_args()

    client = YamcsClient.from_environment()
    processor = client.get_processor(
        instance=os.environ["YAMCS_INSTANCE"],
        processor=os.environ["YAMCS_PROCESSOR"],
    )

    print("Starting LOS for", args.duration, "seconds")
    processor.issue_command("/TSE/simulator/start_los")

    time.sleep(args.duration)

    print("Stopping LOS")
    processor.issue_command("/TSE/simulator/stop_los")


if __name__ == "__main__":
    main()
