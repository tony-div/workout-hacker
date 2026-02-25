const { Jimp } = require('jimp');
const path = require('path');

async function fixIcon() {
    const imagePath = path.resolve('f:/mobile/src/assets/images/workout_hacker_logo.png');
    console.log(`Loading image from: ${imagePath}`);

    try {
        const image = await Jimp.read(imagePath);
        const width = image.bitmap.width;
        const height = image.bitmap.height;
        const size = Math.max(width, height);

        console.log(`Current size: ${width}x${height}. Target size: ${size}x${size}`);

        const square = new Jimp({
            width: size,
            height: size,
            color: 0x00000000 // Transparent
        });

        // Center the original image
        const x = (size - width) / 2;
        const y = (size - height) / 2;

        square.composite(image, x, y);

        await square.write(imagePath);
        console.log('Successfully squared the icon.');
    } catch (error) {
        console.error('Error processing image:', error);
    }
}

fixIcon();
