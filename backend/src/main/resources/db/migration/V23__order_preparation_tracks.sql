update preparation_track
set display_order = case name
    when 'Practice Coding - Design & Implementation for Ambiguous Problems' then 0
    when 'Systems Design Interview Prep & Mocks' then 1
    when 'Governed Agent Project - Incident Response Platform' then 2
    when 'Design Multi-Threaded, Concurrent and Memory Efficient solutions to Production Engineering problems' then 3
    when 'OCP Java SE 17 Developer' then 4
    when 'Learning about Agentic Systems & Incorporating them to worflows' then 5
    when 'Big Data Expertise' then 6
    else 100 + display_order
end;
