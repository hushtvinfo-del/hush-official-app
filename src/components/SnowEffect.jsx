import React, { useEffect, useRef } from 'react';

export default function SnowEffect() {
  const canvasRef = useRef(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    canvas.width = window.innerWidth;
    canvas.height = window.innerHeight;

    const snowflakes = [];
    const numberOfSnowflakes = 100;

    class Snowflake {
      constructor() {
        this.x = Math.random() * canvas.width;
        this.y = Math.random() * canvas.height;
        this.radius = Math.random() * 3 + 1;
        this.speedY = Math.random() * 1 + 0.5;
        this.speedX = Math.random() * 0.5 - 0.25;
        this.opacity = Math.random() * 0.5 + 0.3;
      }

      update() {
        this.y += this.speedY;
        this.x += this.speedX;

        if (this.y > canvas.height) {
          this.y = -10;
          this.x = Math.random() * canvas.width;
        }

        if (this.x > canvas.width) {
          this.x = 0;
        } else if (this.x < 0) {
          this.x = canvas.width;
        }
      }

      draw() {
        ctx.beginPath();
        ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(255, 255, 255, ${this.opacity})`;
        ctx.fill();
        ctx.closePath();
      }
    }

    for (let i = 0; i < numberOfSnowflakes; i++) {
      snowflakes.push(new Snowflake());
    }

    function animate() {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      
      snowflakes.forEach(snowflake => {
        snowflake.update();
        snowflake.draw();
      });

      requestAnimationFrame(animate);
    }

    animate();

    const handleResize = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    };

    window.addEventListener('resize', handleResize);

    return () => {
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      className="fixed inset-0 pointer-events-none z-50"
      style={{ mixBlendMode: 'screen' }}
    />
  );
}