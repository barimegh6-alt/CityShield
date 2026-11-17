"""Single source of truth for the CityShield relational schema.

Column DSL: (name, type, flags)  type in L=BIGINT I=INT D=DOUBLE T=DATETIME S<n>=VARCHAR(n)
flags: pk (auto-increment id), nn (NOT NULL), uq, default=<sql>, check=<sql>, fk=<table>.<col>
Tables are listed in FK-safe load order.  Used by generate_backend_code.py (schema.sql, JPA
entities, repositories) and generate_dataset.py (CSV headers).
"""
TABLES = [
 ("city_zones", "CityZone", [
   ("zone_id","L","pk"),("name","S80","nn uq"),("zone_index","I","nn uq"),("latitude","D","nn check=latitude BETWEEN 18 AND 20"),
   ("longitude","D","nn check=longitude BETWEEN 72 AND 74"),("zone_role","S12","nn check=zone_role IN ('WAREHOUSE','HUB','DEMAND')"),
   ("population","I","nn check=population >= 0")], []),
 ("road_connections", "RoadConnection", [
   ("road_id","L","pk"),("zone_a","L","nn fk=city_zones.zone_id"),("zone_b","L","nn fk=city_zones.zone_id"),
   ("distance_km","D","nn check=distance_km > 0"),("status","S10","nn default='OPEN' check=status IN ('OPEN','BLOCKED')")],
   ["CONSTRAINT ck_road_distinct CHECK (zone_a <> zone_b)","CONSTRAINT uq_road_pair UNIQUE (zone_a, zone_b)","INDEX ix_road_status (status)"]),
 ("distribution_edges", "DistributionEdge", [
   ("edge_id","L","pk"),("from_zone","L","nn fk=city_zones.zone_id"),("to_zone","L","nn fk=city_zones.zone_id"),
   ("capacity_units","I","nn check=capacity_units >= 0")],
   ["CONSTRAINT ck_edge_distinct CHECK (from_zone <> to_zone)","CONSTRAINT uq_edge_pair UNIQUE (from_zone, to_zone)"]),
 ("emergency_centres", "EmergencyCentre", [
   ("centre_id","L","pk"),("name","S100","nn"),("zone_id","L","nn fk=city_zones.zone_id"),
   ("coverage_radius_km","D","nn check=coverage_radius_km > 0"),("cost","D","nn check=cost >= 0"),
   ("status","S12","nn check=status IN ('EXISTING','PROPOSED','UNAVAILABLE')"),("centre_type","S20","nn")], []),
 ("hospitals", "Hospital", [
   ("hospital_id","L","pk"),("name","S120","nn"),("zone_id","L","nn fk=city_zones.zone_id"),
   ("bed_capacity","I","nn check=bed_capacity >= 0"),("beds_available","I","nn check=beds_available >= 0")], []),
 ("emergency_types", "EmergencyType", [
   ("type_id","L","pk"),("code","S24","nn uq"),("name","S60","nn")], []),
 ("emergency_type_team_compat", "EmergencyTypeTeamCompat", [
   ("compat_id","L","pk"),("type_id","L","nn fk=emergency_types.type_id"),("team_type","S16","nn")],
   ["CONSTRAINT uq_compat UNIQUE (type_id, team_type)"]),
 ("resource_types", "ResourceType", [
   ("resource_type_id","L","pk"),("name","S60","nn uq"),("unit","S20","nn")], []),
 ("resource_inventory", "ResourceInventory", [
   ("inventory_id","L","pk"),("zone_id","L","nn fk=city_zones.zone_id"),("resource_type_id","L","nn fk=resource_types.resource_type_id"),
   ("quantity_available","I","nn check=quantity_available >= 0"),("quantity_capacity","I","nn check=quantity_capacity >= 0")],
   ["CONSTRAINT uq_inventory UNIQUE (zone_id, resource_type_id)","CONSTRAINT ck_inv_cap CHECK (quantity_available <= quantity_capacity)"]),
 ("response_teams", "ResponseTeam", [
   ("team_id","L","pk"),("name","S80","nn uq"),("team_type","S16","nn check=team_type IN ('FIRE','MEDICAL','RESCUE','POLICE','HAZMAT','ENGINEERING')"),
   ("base_zone_id","L","nn fk=city_zones.zone_id"),("centre_id","L","fk=emergency_centres.centre_id"),
   ("availability","S12","nn default='AVAILABLE' check=availability IN ('AVAILABLE','BUSY','OFF_DUTY')"),("current_incident_id","L","")],
   ["INDEX ix_team_avail (availability, team_type)"]),
 ("emergency_incidents", "EmergencyIncident", [
   ("incident_id","L","pk"),("incident_code","S20","nn uq"),("type_id","L","nn fk=emergency_types.type_id"),
   ("zone_id","L","nn fk=city_zones.zone_id"),("description","S500","nn"),("severity","I","nn check=severity BETWEEN 1 AND 5"),
   ("urgency","I","nn check=urgency BETWEEN 1 AND 5"),("affected_people","I","nn check=affected_people >= 0"),
   ("status","S12","nn check=status IN ('REPORTED','ASSIGNED','IN_PROGRESS','RESOLVED','CANCELLED','ARCHIVED')"),
   ("reported_at","T","nn"),("created_at","T","nn"),("updated_at","T","nn")],
   ["INDEX ix_inc_status (status)","INDEX ix_inc_zone (zone_id)","INDEX ix_inc_reported (reported_at)"]),
 ("incident_resource_requirements", "IncidentResourceRequirement", [
   ("requirement_id","L","pk"),("incident_id","L","nn fk=emergency_incidents.incident_id"),
   ("resource_type_id","L","nn fk=resource_types.resource_type_id"),("quantity_required","I","nn check=quantity_required > 0")],
   ["CONSTRAINT uq_requirement UNIQUE (incident_id, resource_type_id)"]),
 ("incident_status_history", "IncidentStatusHistory", [
   ("history_id","L","pk"),("incident_id","L","nn fk=emergency_incidents.incident_id"),("old_status","S12",""),
   ("new_status","S12","nn"),("changed_at","T","nn"),("note","S200","")], ["INDEX ix_hist_incident (incident_id, changed_at)"]),
 ("incident_logs", "IncidentLog", [
   ("log_id","L","pk"),("incident_id","L","nn fk=emergency_incidents.incident_id"),("log_text","S400","nn"),("logged_at","T","nn")],
   ["INDEX ix_log_incident (incident_id)"]),
 ("incident_assignments", "IncidentAssignment", [
   ("assignment_id","L","pk"),("incident_id","L","nn fk=emergency_incidents.incident_id"),("team_id","L","nn fk=response_teams.team_id"),
   ("status","S12","nn check=status IN ('ACTIVE','RELEASED','COMPLETED')"),("assigned_at","T","nn")],
   ["INDEX ix_assign_team (team_id, status)","INDEX ix_assign_incident (incident_id, status)"]),
 ("resource_allocations", "ResourceAllocation", [
   ("allocation_id","L","pk"),("resource_type_id","L","nn fk=resource_types.resource_type_id"),("from_zone","L","nn fk=city_zones.zone_id"),
   ("to_zone","L","nn fk=city_zones.zone_id"),("quantity","I","nn check=quantity > 0"),("created_at","T","nn"),("batch_ref","S40","")], []),
 ("relief_destinations", "ReliefDestination", [
   ("destination_id","L","pk"),("zone_id","L","nn uq fk=city_zones.zone_id"),("demand_units","I","nn check=demand_units >= 0")], []),
 ("delivery_routes", "DeliveryRoute", [
   ("route_id","L","pk"),("depot_centre_id","L","nn fk=emergency_centres.centre_id"),("algorithm","S30","nn"),
   ("total_km","D","nn check=total_km >= 0"),("created_at","T","nn")], []),
 ("delivery_route_stops", "DeliveryRouteStop", [
   ("stop_id","L","pk"),("route_id","L","nn fk=delivery_routes.route_id"),("stop_order","I","nn check=stop_order >= 0"),
   ("zone_id","L","nn fk=city_zones.zone_id")], ["CONSTRAINT uq_stop UNIQUE (route_id, stop_order)"]),
 ("algorithm_execution_logs", "AlgorithmExecutionLog", [
   ("log_id","L","pk"),("algorithm","S40","nn"),("run_type","S12","nn check=run_type IN ('API','BENCHMARK','TEST')"),
   ("input_size","I","nn"),("duration_ms","D","nn"),("summary","S300",""),("run_at","T","nn")],
   ["INDEX ix_exec_algo (algorithm, run_at)"]),
 ("benchmark_results", "BenchmarkResult", [
   ("result_id","L","pk"),("algorithm","S40","nn"),("variant","S40","nn"),("input_size","I","nn"),("median_ms","D","nn"),
   ("min_ms","D","nn"),("max_ms","D","nn"),("repetitions","I","nn"),("note","S300",""),("machine","S200",""),("run_at","T","nn")],
   ["INDEX ix_bench_algo (algorithm, variant, input_size)"]),
]
# tables that have a CSV seed file
NO_SEED = {"delivery_routes","delivery_route_stops","resource_allocations","algorithm_execution_logs","benchmark_results"}
