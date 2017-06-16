/*
 * Copyright 2015 Vitaly Litvak (vitavaque@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
// Manu: Fuel Comsumption
 */
package org.traccar.web.server.reports;

import org.traccar.web.shared.model.UserSettings;

import org.traccar.web.shared.model.AccessDeniedException;
import org.traccar.web.shared.model.Device;
import org.traccar.web.shared.model.Position;
import org.traccar.web.shared.model.Report;
import org.traccar.web.shared.model.Refuel;

import java.io.IOException;
import java.util.*;

public class ReportFC extends ReportGenerator {
    @Override
    void generateImpl(Report report) throws IOException {
        h2(report.getName());

        //
        // There are two posible reports: detailed (details == true) or summary (details == false)
        //
        
        // for summaries we first build a panel and table for all the devices
        if (!report.isDetails()){   
            drawPanel ("Resumen de consumo por dispositivo", report.getFromDate(), report.getToDate());
            if (!report.isDetails()) {
                drawTableHead(new String[] {"Vehiculo", "Matricula", "Num Repostajes", "litros", "Km", "l/100Km"} );
                // body
                tableBodyStart();
            }
        }
        
        //
        // Loop for every selected device
        //
        for (Device device : getDevices(report)) {
            
           // We fill the refuelList from an HQL sentence
     
           List<Refuel> refuels = entityManager.createQuery(
                    "select r from Refuel r " +
                    "where r.plateNumber = :plateNumber  and r.refuelTime BETWEEN :from AND :to ORDER BY r.refuelTime", Refuel.class)
                    .setParameter("plateNumber", device.getPlateNumber())
                    .setParameter("from", report.getFromDate())
                    .setParameter("to", report.getToDate())
                    .getResultList();
           
           // on detailed reports we print one row for each refuel
           if (report.isDetails()) {
                drawPanel(device.getName(),report.getFromDate(), report.getToDate());
                // device details
                deviceDetails(device);
                // data table
                if (!refuels.isEmpty()) {
                    drawDeviceData(refuels, device, report.isDetails());              
                }
                panelBodyEnd();
                panelEnd();
            } else {
                if (!refuels.isEmpty()) {
                    // With details=false this will print one row per device in the List
                    drawDeviceData(refuels, device, report.isDetails()); 
                } 
           }
        }
        // After the device's loop we close the table and panel if it is a summary
        if (!report.isDetails()){
            tableBodyEnd();
            tableEnd();
            panelBodyEnd();
            panelEnd();
        }
    } 
    
    void drawPanel (String panelTitle , Date from, Date to) {
            
        panelStart();
        // heading
        panelHeadingStart();
        text(panelTitle);
        panelHeadingEnd();
        // body
        panelBodyStart();
        // period
        paragraphStart();
        bold(message("timePeriod") + ": ");
        text(formatDate(from) + " - " + formatDate(to));
        paragraphEnd();
        
    }
    
    void drawTableHead(String headerFields[]) {
        
        tableStart(hover().condensed());
        // header
        tableHeadStart();
        tableRowStart();

        for (String header : headerFields) {
            tableHeadCellStart();
            text(header);
            tableHeadCellEnd();
        }   
        tableRowEnd();
        tableHeadEnd();
    }
    
     void drawDeviceData(List<Refuel> refuels, Device device, boolean showDetails) {
     
        // We only print the header of each refuel if "details" is selected
        if (showDetails) {   
            drawTableHead(new String[] {"date", "litros", "Km", "l/100Km"});
            // body
            tableBodyStart();   
        }
        
        // Code to calculate data
        
        Refuel prevRefuel = null;
        double distance = 0;
        float fuelAverage = 0;
        double totalDistance = 0;
        float totalLitros = 0;
        
        UserSettings.SpeedUnit speedUnit = currentUser.getUserSettings().getSpeedUnit();
        UserSettings.DistanceUnit distanceUnit = speedUnit.getDistanceUnit();
            
        distanceUnit = speedUnit.getDistanceUnit();
        
        for (Iterator<Refuel> it = refuels.iterator(); it.hasNext(); ) {
            Refuel refuel = it.next();    
            
            if ((distance = refuel.getDistance()) == 0) {
                // We get the first refuel as prevRefuel and next one as
                // refuel. We've got to dates so we can query the position between them.
                if (prevRefuel != null) {
                    distance = getDistance (prevRefuel.getRefuelTime(), refuel.getRefuelTime(), device);
                    if (distance > 0) {
                        // We store the calculated distance in the db for future queries
                        entityManager.createQuery("UPDATE Refuel r SET r.distance=:dist WHERE r.id=:id")
                                .setParameter("dist", distance)
                                .setParameter("id", refuel.getId())
                                .executeUpdate();
                    }
                }
            }
            // We calculate distance dependent values
            if (distance > 0) {
                fuelAverage = refuel.getLitros() / (float) distance * (float) distanceUnit.getFactor() * 100;
                totalDistance += distance;
                totalLitros += refuel.getLitros();
            }
    
            // We show the details depending the user's seleccion
            if (showDetails) {
                // print each row 
                tableRowStart();
                tableCell(formatDate(refuel.getRefuelTime()));
                tableCell(String.valueOf(refuel.getLitros()));
                if (distance > 0) tableCell(formatDistance(distance));
                    else tableCell("N/A");
                if (fuelAverage > 0) tableCell(String.valueOf(fuelAverage));
                    else tableCell("N/A");
                tableRowEnd();
            }
            
            prevRefuel=refuel; 
        }
        
        if (showDetails) {
            // End tablem for each refuel
            tableBodyEnd();
            tableEnd();
            drawDeviceSummary(refuels.size(), totalLitros, totalDistance, distanceUnit.getFactor());
        } else {
            // details==false so We just write one row for each device
            tableRowStart();
            tableCell(device.getName());
            tableCell(device.getPlateNumber());
            tableCell(String.valueOf(refuels.size()));
            tableCell(String.valueOf(totalLitros));
            tableCell(formatDistance(totalDistance));
            if ((totalLitros > 0 ) && (totalDistance > 0)) {
                tableCell (String.valueOf(totalLitros / (float) totalDistance * (float) distanceUnit.getFactor() * 100));
            } else {
                tableCell ("N/A");
            }
            tableRowEnd();
        }       
     }              
     
     //
     // Device summary info
     //
    private void drawDeviceSummary(int refuelNumber,
                             float totalLitros,
                             double totalDistance,
                             double distanceUnitFactor) {
        tableStart();
        tableBodyStart();

        dataRow ("Refuels Number", String.valueOf(refuelNumber));
        dataRow ("Total Litros", totalLitros + " litros");
        dataRow ("Distancia", formatDistance(totalDistance));
        if ((totalLitros > 0 ) && (totalDistance > 0)) {
            dataRow ("Consumo medio",  String.valueOf(totalLitros / (float) totalDistance * (float) distanceUnitFactor * 100) + " l/100km");
        }
        tableBodyEnd();
        tableEnd();
    }

    
    double getDistance (java.util.Date from, java.util.Date to, Device device) {
        List<Position> positions;
        double distance = 0;
        
        try {
              positions = dataService.getPositions(device, from , to, true);
            } catch (AccessDeniedException ade) {
               return distance; 
            }
        
        if (!positions.isEmpty()) {
        
            Position start = null;
            Position end = null;
            Position prevPosition = null;
        
            for (Iterator<Position> it = positions.iterator(); it.hasNext(); ) {
                Position position = it.next();

                if (isMoving(position)) {
                    if (start == null) {
                        start = position;
                    }
                    end = position;
                }
                distance += position.getDistance();
                prevPosition = position;
            }
        }
            return distance;
    }
    
    boolean isMoving(Position position) {
        return position.getSpeed() != null && position.getSpeed() > position.getDevice().getIdleSpeedThreshold();
    }
    
}