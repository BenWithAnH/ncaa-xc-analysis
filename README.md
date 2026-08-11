# NCAA Cross Country Analysis

A full-stack web application designed to scrape, analyze, and rate NCAA Cross Country race results from TFRRS. The application calculates Course Adjustment Factors (CAF) for different meets and provides athlete ratings based on their performances.

## Project Structure

The project consists of two main components:
- **Backend**: A Java Spring Boot application that handles scraping, data processing, and exposes REST APIs.
- **Frontend**: A Vue.js 3 single-page application built with Vite.

## Features

- **Race Scraping**: Scrape raw race results (names, times, links) directly from TFRRS meet URLs.
- **Athlete Rating & CAF Calculation**: Automatically calculate a Course Adjustment Factor (CAF) for a meet and assign ratings to athletes based on their times and fatigue coefficients.
- **Bulk Meet Scraping**: Discover and bulk scrape multiple cross country meets from TFRRS into a PostgreSQL database.
- **Athlete Profiles**: Fetch an athlete's personal records (PRs) and prior ratings from their TFRRS profile.

## Prerequisites

- **Java**: JDK 17 or higher
- **Node.js**: v22+ or v24+
- **PostgreSQL**: A running instance of PostgreSQL (configure credentials in `application.properties`)
- **Maven**: To build and run the backend

## Getting Started

### 1. Backend Setup

The backend is a Spring Boot application located in the `demo` directory.

1. Navigate to the `demo` directory:
   ```bash
   cd demo
   ```
2. Configure your database settings (URL, username, password) in `src/main/resources/application.properties` (if not already set).
3. Run the backend using Maven:
   ```bash
   mvn spring-boot:run
   ```
   The API will be available at `http://localhost:8080/api/`.

### 2. Frontend Setup

The frontend is a Vue 3 application located in the `demo/frontend/vue-project` directory.

1. Navigate to the frontend directory:
   ```bash
   cd demo/frontend/vue-project
   ```
2. Install the dependencies:
   ```bash
   npm install
   ```
3. Start the development server:
   ```bash
   npm run dev
   ```
   The frontend will be available at `http://localhost:5173/` (or the port specified by Vite).

## API Endpoints

- `GET /api/athletes/top`: Fetches the top athletes ranked by their rating.
- `POST /api/race/rate`: Scrapes a meet, calculates the CAF, and returns rated athletes.
- `GET /api/race/results`: Scrapes raw race results without rating.
- `GET /api/meets`: Lists available XC meets from TFRRS.
- `POST /api/meets/bulk-scrape`: Bulk scrapes multiple meets from TFRRS and saves results to the database.
- `GET /api/athlete/profile`: Fetches an athlete's PRs and prior rating from their TFRRS profile.

## Technologies Used

- **Backend**: Spring Boot, Spring Data JPA, PostgreSQL, Jsoup (HTML scraping), OpenCSV, Gson
- **Frontend**: Vue 3, Vite, Axios, ESLint, Prettier
