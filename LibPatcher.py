"""
MIT License

Copyright (c) [2023] [MuhammadRizwan]

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
"""

import subprocess
import sys
import socket
import importlib
import re
import time
import platform

def welcome():
    """
    Displays a welcome message.

    Prints a welcome message along with tool information and external links for more information, feedback, and suggestions.
    """
    welcome_message = """
\033[1;32mWelcome to
  _     _ _     ____       _       _               
 | |   (_) |__ |  _ \ __ _| |_ ___| |__   ___ _ __ 
 | |   | | '_ \| |_) / _` | __/ __| '_ \ / _ \ '__|
 | |___| | |_) |  __/ (_| | || (__| | | |  __/ |   
 |_____|_|_.__/|_|   \__,_|\__\___|_| |_|\___|_|   
                                                   
                                            v1.0\033[0m
"""
    print(welcome_message)
    print("\033[1;35mThis tool is designed by Indian guy MuhammedRizwan to make patching easier by providing a simple solution to setting all the necessary values.\n\nIt modifies specified offsets in the binary to return values such as boolean true/false, integer zero/max, long zero/64-bit, float/double zero and void with nop.\n\nThe tool mainly relies on the radare2 framework and the r2pipe module to perform the binary patching.\n\nVisit for more Info\033[0m\n`https://TDOhex.t.me`\n\033[1;35mFor feedback and suggestions\033[0m\n`https://TDOhex_Discussion.t.me`")

def check_radare2_installed():
    """
    Checks if radare2 is installed.

    Attempts to execute the 'r2 -v' command using subprocess and checks if it returns without error.
    If radare2 is not installed, an error message is printed with instructions to install it.
    """
    try:
        subprocess.check_output(['r2', '-v'], stderr=subprocess.STDOUT)
    except Exception as e:
        print("\n\033[1;31mradare2 is not installed. Program terminated.\nEnter this command for installing radare2\033[0m\n`pkg install radare2 -y`\n\033[1;31mOr go to\033[0m\n`https://github.com/radareorg/radare2`")
        sys.exit(1)

def check_internet_connection():
    """
    Checks internet connection.

    Attempts to create a socket connection to Google's DNS server (8.8.8.8) to check if there is an active internet connection.
    Returns True if connection is successful, False otherwise.
    """
    try:
        socket.create_connection(("8.8.8.8", 53), timeout=5)
        return True
    except socket.error:
        return False

def install_r2pipe():
    """
    Installs r2pipe.

    Uses the 'pip' module to install r2pipe package.
    Prints a success message if installation is complete.
    """
    try:
        importlib.import_module("pip").main(["install", "r2pipe"])
        print("\n\033[1;32mr2pipe installation complete.\033[0m")
        import r2pipe
    except Exception as e:
        print(f"\n\033[1;31mFailed to install r2pipe: {str(e)}\033[0m")
        sys.exit(1)

def check_r2pipe_installed():
    """
    Checks if r2pipe is installed.

    Attempts to import r2pipe module. If it fails, an error message is printed with the option to install r2pipe.
    If the user chooses to install r2pipe, it checks the internet connection and proceeds with the installation.
    """
    try:
        import r2pipe
    except ImportError:
        print("\n\033[1;31mr2pipe is not installed.\033[0m")
        response = input("\n\033[1;33mDo you want to install r2pipe? (y/n): \033[0m")
        if response.lower() == "y":
            if check_internet_connection():
                global r2pipe
                r2pipe = install_r2pipe()
            else:
                print("\n\033[1;31mNo internet connection. Cannot install r2pipe. Program terminated.\033[0m")
                sys.exit(1)
        else:
            print("\n\033[1;31mr2pipe installation skipped.\nProgram terminated.\033[0m")
            sys.exit()

def prompt_file_path():
    """
    Prompts the user to enter the executable file path.

    The function asks the user to provide the path of the executable file and stores it in the global variable 'file_path'.
    If the user enters an empty path, it prompts again until a valid path is provided.
    """
    global file_path
    print("\n\033[1;36m// Remember, arm64-v8a and armeabi-v7a are acceptable.\033[0m")
    file_path = input("\n\033[1;33mEnter executable path: \033[0m")
    while not file_path:
        print("\033[1;31mFile path empty.\033[0m")
        file_path = input("\n\033[1;33mEnter executable path (or 'x' to terminate): \033[0m")
        if file_path.lower() == 'x':
            print("\n\033[1;31mProgram terminated by user.\033[0m")
            sys.exit()

def check_file_architecture(file_path):
    """
    Checks the architecture of the executable file.

    Opens the file using r2pipe and retrieves the architecture information.
    Returns the architecture string if available, None otherwise.
    """
    try:
        r2 = r2pipe.open(file_path)
        info_json = r2.cmdj("ij")
        arch = info_json.get("bin", {}).get("machine", "")
        r2.quit()
        if not arch:
            print("\033[1;31mFile is not executable.\033[0m")
            return None
        else:
            return arch
    except Exception as e:
        print(f"\n\033[1;31mAn error occurred while reading the file: {str(e)}\033[0m")
        return None

def print_architecture():
    """
    Prints the detected architecture of the file.

    Continuously checks the architecture of the file using the 'check_file_architecture' function until a valid architecture is detected.
    If the architecture is 'arm64-v8a', it prints a message indicating that arm64 patches will be applied.
    If the architecture is 'armeabi-v7a', it prints a message indicating that arm32 patches will be applied.
    If the architecture is 'x86-64', 'Intel', 'MIPS', or not available, it prompts the user to enter a new file path.
    """
    global detected_arch
    while True:
        detected_arch = check_file_architecture(file_path)

        if detected_arch is not None and "aarch64" in detected_arch:
            print("\n\033[1;32mDetected architecture: arm64-v8a\nPatches of arm64 will be applied.\033[0m")
            break  # Skip to the next iteration of the while loop
        elif detected_arch is not None and "ARM" in detected_arch:
            print("\n\033[1;32mDetected architecture: armeabi-v7a\nPatches of arm32 will be applied.\033[0m")
            break  # Skip to the next iteration of the while loop
        elif detected_arch is not None and ("x86-64" in detected_arch or "Intel" in detected_arch or "MIPS" in detected_arch):
            print("\n\033[1;31mDetected architecture:", detected_arch, "\nPatches are not available.\033[0m")
            prompt_file_path()
        elif detected_arch is None:
            print("\033[1;31mInvalid file path.\033[0m")
            prompt_file_path()

def validate_offsets(offsets):
    """
    Validates the format of the provided offsets.

    Checks if the provided offsets are in the correct format (hexadecimal values starting with '0x').
    Returns True if the offsets are valid, False otherwise.
    """
    if not offsets:
        return True
    pattern = r"^0x[0-9a-fA-F]+$"
    offset_list = offsets.split(",")
    for offset in offset_list:
        if not re.match(pattern, offset.strip()):
            return False
    return True

def prompt_offsets(offset_type):
    """
    Prompts the user to enter offsets for a specific offset type.

    Asks the user to provide the offsets for the given offset type.
    Validates the format of the provided offsets using the 'validate_offsets' function.
    Continues to prompt the user until valid offsets are provided.
    Returns the validated offsets.
    """
    offsets = input(f"\033[1;33mEnter offsets for {offset_type}: \033[0m")
    while not validate_offsets(offsets):
        print("\033[1;31mInvalid offsets format. Please enter valid offsets.\033[0m")
        offsets = input(f"\n\033[1;33mEnter offsets for {offset_type}:\033[0m\033[90m(e.g. 0x100 or 0x100,0x200)\033[0m")
    return offsets

def prompt_all_offset_types():
    """
    Prompts the user to enter offsets for all offset types.

    Displays the offset types and asks the user to provide offsets for each type.
    Uses the 'prompt_offsets' function to prompt the user and validate the offsets.
    Returns a dictionary containing the offsets for each offset type.
    """
    print("\n\033[1;36m// Leave empty if not necessary.\n// Separate by commas if there are multiple offsets.\033[0m\n")
    global offsets
    offsets = {}
    offset_types = [
        "boolean_true",
        "boolean_false",
        "integer_zero",
        "integer16_max",
        "integer32_max",
        "long_zero",
        "long_64",
        "float_zero",
        "double_zero",
        "void_nop",
    ]
    for offset_type in offset_types:
        offsets[offset_type] = prompt_offsets(offset_type)
    return offsets

def set_value_at_offset(r2, offset, data_type, value, detected_arch):
    """
    Sets the value at the specified offset in the binary using radare2.

    Args:
        r2 (r2pipe): The radare2 instance.
        offset (str): The offset to set the value at.
        data_type (str): The type of data being set.
        value (str): The value to set at the offset.
        detected_arch (str): The detected architecture of the binary.

    Prints a message indicating that the value has been set at the offset.
    """
    r2.cmd(f"wx {value} @ {offset}")
    print(f"\033[1;32mValue of {data_type} has been set at offset {offset}.\033[0m")

def set_values_to_offsets(file_path, offsets):
    """
    Sets the values at the specified offsets in the binary using radare2.

    Args:
        file_path (str): The path to the binary file.
        offsets (dict): A dictionary containing the offsets for each offset type.

    Opens the binary file using radare2, enables writing mode, and sets the values at the specified offsets.
    Prints a message indicating the completion of setting values.

    Note: This function assumes the availability of the 'get_value_by_data_type' function.

    Raises:
        Exception: If an error occurs while processing the binary.

    Returns:
        None
    """
    start_time = time.time()
    r2 = r2pipe.open(file_path)
    r2.cmd("oo+")

    for data_type, offset_list in offsets.items():
        if offset_list:
            for offset in offset_list.split(","):
                value = get_value_by_data_type(data_type, detected_arch)
                if value is None:
                    print(f"\n\033[1;31mInvalid data type: {data_type}. Skipping offset {offset}.\033[0m")
                    continue

                set_value_at_offset(r2, offset.strip(), data_type, value, detected_arch)

    r2.quit()
    elapsed_time = time.time() - start_time
    print(f"\n\033[1;32mFinished setting values. Elapsed time: {elapsed_time:.2f} seconds.\033[0m")

def get_value_by_data_type(data_type, detected_arch):
    """
    Retrieves the value based on the data type and detected architecture.

    Args:
        data_type (str): The type of data.
        detected_arch (str): The detected architecture of the binary.

    Returns:
        str: The value corresponding to the data type and architecture.

    Note: This function assumes the availability of the detected architecture.
    If the data type is not supported, None is returned.
    """
    if data_type == "boolean_true":
        return "20008052C0035FD6" if detected_arch.endswith("aarch64") else "0100A0E31EFF2FE1"
    elif data_type == "boolean_false":
        return "00008052C0035FD6" if detected_arch.endswith("aarch64") else "0000A0E31EFF2FE1"
    elif data_type == "integer_zero":
        return "00008052C0035FD6" if detected_arch.endswith("aarch64") else "0000A0E31EFF2FE1"
    elif data_type == "integer16_max":
        return "E0FF8F52C0035FD6" if detected_arch.endswith("aarch64") else "FF0F07E31EFF2FE1"
    elif data_type == "integer32_max":
        return "E0FF9F52E0FFAF72C0035FD6" if detected_arch.endswith("aarch64") else "FF0F0FE3FF0F47E31EFF2FE1"
    elif data_type == "long_zero":
        return "00008052C0035FD6" if detected_arch.endswith("aarch64") else "0000A0E31EFF2FE1"
    elif data_type == "long_64":
        return "E0FF9FD2E0FFBFF2E00FC0F2C0035FD6" if detected_arch.endswith("aarch64") else "0201E0E31EFF2FE1"
    elif data_type == "float_zero":
        return "E003271EC0035FD6" if detected_arch.endswith("aarch64") else "0000A0E31EFF2FE1"
    elif data_type == "double_zero":
        return "E003679EC0035FD6" if detected_arch.endswith("aarch64") else "0000A0E31EFF2FE1"
    elif data_type == "void_nop":
        return "C0035FD6" if detected_arch.endswith("aarch64") else "A0E31EFF2FE1"
    else:
        return None

def prompt_open_link():
    """
    Prompt the user to visit TDOhex website and open it in the default browser if using Linux.

    This function checks the user's operating system. If the system is Linux, it prompts the user
    to visit the TDOhex website. If the user enters '1', the website is opened in the default
    browser. Otherwise, a message about TDOhex is printed. If the system is not Linux, the same
    message is printed.

    Note: This function assumes the availability of the 'subprocess' and 'platform' module (both are parts of standard python lib).

    Returns:
        None
    """
    message = "\n\033[1mHappy reversing!\nDon't forget to visit TDOhex.\nWe are trying to provide useful knowledge and ways to understand patching and modding of Android apps and games.\033[0m"
    if platform.system() == "Linux":
        link = "https://TDOhex.t.me"
        user_input = input("\n\n\033[1;33mEnter 1 to visit TDOhex, or any other key to terminate: \033[0m")
        if user_input == "1":
            subprocess.run(["xdg-open", link])
            print("\n\033[1;35mHappy reversing!\nThanks to visit!\033[0m")
        else:
            print(message)
    else:
        print(message)

def main():
    """
    The main function that orchestrates the execution of the program.

    This function executes the following steps:
    1. Displays a welcome message.
    2. Checks if radare2 is installed.
    3. Checks if r2pipe is installed.
    4. Prompts the user to enter the executable file path.
    5. Prints the detected architecture of the file.
    6. Prompts the user to enter offsets for different data types.
    7. Sets the specified values to the specified offsets in the file.
    8. Prompts the user to open a specific link.

    Note: This function assumes the availability of the ['subprocess', 'sys', 'socket', 'importlib', 're', 'time', 'platform', (are parts of standard python lib)], and 'r2pipe' modules.

    Returns:
        None
    """
    welcome()
    check_radare2_installed()
    check_r2pipe_installed()
    prompt_file_path()
    print_architecture()
    prompt_all_offset_types()
    set_values_to_offsets(file_path, offsets)
    prompt_open_link()

if __name__ == "__main__":
    main()