# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure("2") do |config|
  # Specify the box to use
  config.vm.box = "generic/ubuntu2004"
  config.vm.synced_folder "./", "/vagrant", type: "rsync"


  # Configure the VirtualBox provider
  config.vm.provider "libvirt" do |libvirt|
    libvirt.memory = "1024"
    libvirt.cpus = 2
  end

  config.vm.provision :docker

  # Provisioning to build and run Docker container
  config.vm.provision "shell", privileged: false, inline: <<-SHELL
    # Build Docker image from current directory
    cd /vagrant
    docker build -t my_application .

    # Run Docker container, forwarding port 9100
    docker run -d -p 9100:9100 --name my_running_app my_application
  SHELL
end
