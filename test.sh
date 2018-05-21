ip = "http://34.229.151.88:8000"
wget "$ip/mzrun.html?m=Maze500.maze&x0=249&y0=250&x1=455&y1=488&v=57&s=astar" # L
wget "$ip/mzrun.html?m=Maze250.maze&x0=124&y0=125&x1=199&y1=201&v=75&s=bfs" # M

wget "$ip/mzrun.html?m=Maze50.maze&x0=0&y0=1&x1=6&y1=6&v=75&s=bfs" # XS x0=0
wget "$ip/mzrun.html?m=Maze50.maze&x0=1&y0=1&x1=6&y1=6&v=75&s=bfs" # XS x0=1
wget "$ip/mzrun.html?m=Maze50.maze&x0=2&y0=1&x1=6&y1=6&v=75&s=bfs" # XS x0=2

wget "$ip/mzrun.html?m=Maze50.maze&x0=0&y0=1&x1=6&y1=6&v=75&s=astar" # XS astar
wget "$ip/mzrun.html?m=Maze50.maze&x0=1&y0=1&x1=6&y1=6&v=75&s=astar" # XS
wget "$ip/mzrun.html?m=Maze50.maze&x0=2&y0=1&x1=6&y1=6&v=75&s=astar" # XS

wget "$ip/mzrun.html?m=Maze50.maze&x0=0&y0=1&x1=6&y1=6&v=100&s=bfs" # XS speed 100
wget "$ip/mzrun.html?m=Maze50.maze&x0=1&y0=1&x1=6&y1=6&v=100&s=bfs" # XS
wget "$ip/mzrun.html?m=Maze50.maze&x0=2&y0=1&x1=6&y1=6&v=100&s=bfs" # XS
wget "$ip/mzrun.html?m=Maze50.maze&x0=0&y0=1&x1=6&y1=6&v=100&s=astar" # XS
wget "$ip/mzrun.html?m=Maze50.maze&x0=1&y0=1&x1=6&y1=6&v=100&s=astar" # XS
wget "$ip/mzrun.html?m=Maze50.maze&x0=2&y0=1&x1=6&y1=6&v=100&s=astar" # XS

wget "$ip/mzrun.html?m=Maze100.maze&x0=1&y0=1&x1=74&y1=95&v=40&s=bfs" # S #equal requests
wget "$ip/mzrun.html?m=Maze100.maze&x0=1&y0=1&x1=74&y1=95&v=40&s=bfs" # S
wget "$ip/mzrun.html?m=Maze100.maze&x0=1&y0=1&x1=74&y1=95&v=40&s=bfs" # S