RatCrystalNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		if player.quest["reeves_quest"] == 5 then
			player.quest["reeves_quest"] = 6
			player:addItem("crystal_shard", 1, 0, player.ID)
			player:dialogSeq(
				{
					t,
					"Kristal itu meluapkan kekuatan begitu telapak tanganmu menyentuhnya!",
					"Saat kau menarik tangan menahan sakit, tampak serpihan kristal kecil menancap di telapakmu."
				},
				0
			)
		end
		player:dialogSeq({t, "Kristal itu tampak tidur."}, 0)
	end),
	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)
		if player.quest["reeves_quest"] >= 4 then
			if speech == "malapetaka" then
				if player.quest["reeves_quest"] == 4 then
					player.quest["reeves_quest"] = 5
				end
				player:dialogSeq(
					{
						t,
						"Kristal itu memancarkan cahaya samar. Kau hampir tergoda menyentuhnya..."
					},
					0
				)
			end
		end
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
	end),
}

TigerCrystalNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		if player.quest["reeves_quest"] == 5 then
			player.quest["reeves_quest"] = 6
			player:dialogSeq(
				{
					t,
					"Kristal itu meluapkan kekuatan begitu telapak tanganmu menyentuhnya!",
					"Saat kau menarik tangan menahan sakit, tampak serpihan kristal kecil menancap di telapakmu."
				},
				0
			)
			player:addItem("crystal_shard", 1, 0, player.ID)
		end
		player:dialogSeq({t, "Kristal itu tampak tidur."}, 0)
	end),
	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)
		if player.quest["reeves_quest"] >= 4 then
			if speech == "malapetaka" then
				if player.quest["reeves_quest"] == 4 then
					player.quest["reeves_quest"] = 5
				end
				player:dialogSeq(
					{
						t,
						"Kristal itu memancarkan cahaya samar. Kau hampir tergoda menyentuhnya."
					},
					0
				)
			end
		end
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
	end),
}

DogCrystalNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		if player.quest["reeves_quest"] == 5 then
			player.quest["reeves_quest"] = 6
			player:dialogSeq(
				{
					t,
					"Kristal itu meluapkan kekuatan begitu telapak tanganmu menyentuhnya!",
					"Saat kau menarik tangan menahan sakit, tampak serpihan kristal kecil menancap di telapakmu."
				},
				0
			)
			player:addItem("crystal_shard", 1, 0, player.ID)
		end
		player:dialogSeq({t, "Kristal itu tampak tidur."}, 0)
	end),
	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)
		if player.quest["reeves_quest"] >= 4 then
			if speech == "malapetaka" then
				if player.quest["reeves_quest"] == 4 then
					player.quest["reeves_quest"] = 5
				end
				player:dialogSeq(
					{
						t,
						"Kristal itu memancarkan cahaya samar. Kau hampir tergoda menyentuhnya."
					},
					0
				)
			end
		end
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
	end),
}
DragonCrystalNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		if player.quest["reeves_quest"] == 5 then
			player.quest["reeves_quest"] = 6
			player:dialogSeq(
				{
					t,
					"Kristal itu meluapkan kekuatan begitu telapak tanganmu menyentuhnya!",
					"Saat kau menarik tangan menahan sakit, tampak serpihan kristal kecil menancap di telapakmu."
				},
				0
			)
			player:addItem("crystal_shard", 1, 0, player.ID)
		end
		player:dialogSeq({t, "Kristal itu tampak tidur."}, 0)
	end),
	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)
		if player.quest["reeves_quest"] >= 4 then
			if speech == "malapetaka" then
				if player.quest["reeves_quest"] == 4 then
					player.quest["reeves_quest"] = 5
				end
				player:dialogSeq(
					{
						t,
						"Kristal itu memancarkan cahaya samar. Kau hampir tergoda menyentuhnya."
					},
					0
				)
			end
		end
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
	end),
}
SnakeCrystalNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		if player.quest["reeves_quest"] == 5 then
			player.quest["reeves_quest"] = 6
			player:dialogSeq(
				{
					t,
					"Kristal itu meluapkan kekuatan begitu telapak tanganmu menyentuhnya!",
					"Saat kau menarik tangan menahan sakit, tampak serpihan kristal kecil menancap di telapakmu."
				},
				0
			)
			player:addItem("crystal_shard", 1, 0, player.ID)
		end
		player:dialogSeq({t, "Kristal itu tampak tidur."}, 0)
	end),
	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)
		if player.quest["reeves_quest"] >= 4 then
			if speech == "malapetaka" then
				if player.quest["reeves_quest"] == 4 then
					player.quest["reeves_quest"] = 5
				end
				player:dialogSeq(
					{
						t,
						"Kristal itu memancarkan cahaya samar. Kau hampir tergoda menyentuhnya."
					},
					0
				)
			end
		end
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
	end),
}
RoosterCrystalNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		if player.quest["reeves_quest"] == 5 then
			player.quest["reeves_quest"] = 6
			player:dialogSeq(
				{
					t,
					"Kristal itu meluapkan kekuatan begitu telapak tanganmu menyentuhnya!",
					"Saat kau menarik tangan menahan sakit, tampak serpihan kristal kecil menancap di telapakmu."
				},
				0
			)
			player:addItem("crystal_shard", 1, 0, player.ID)
		end
		player:dialogSeq({t, "Kristal itu tampak tidur."}, 0)
	end),
	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)
		if player.quest["reeves_quest"] >= 4 then
			if speech == "malapetaka" then
				if player.quest["reeves_quest"] == 4 then
					player.quest["reeves_quest"] = 5
				end
				player:dialogSeq(
					{
						t,
						"Kristal itu memancarkan cahaya samar. Kau hampir tergoda menyentuhnya."
					},
					0
				)
			end
		end
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
	end),
}
SheepCrystalNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		if player.quest["reeves_quest"] == 5 then
			player.quest["reeves_quest"] = 6
			player:dialogSeq(
				{
					t,
					"Kristal itu meluapkan kekuatan begitu telapak tanganmu menyentuhnya!",
					"Saat kau menarik tangan menahan sakit, tampak serpihan kristal kecil menancap di telapakmu."
				},
				0
			)
			player:addItem("crystal_shard", 1, 0, player.ID)
		end
		player:dialogSeq({t, "Kristal itu tampak tidur."}, 0)
	end),
	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)
		if player.quest["reeves_quest"] >= 4 then
			if speech == "malapetaka" then
				if player.quest["reeves_quest"] == 4 then
					player.quest["reeves_quest"] = 5
				end
				player:dialogSeq(
					{
						t,
						"Kristal itu memancarkan cahaya samar. Kau hampir tergoda menyentuhnya."
					},
					0
				)
			end
		end
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
	end),
}
MonkeyCrystalNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		if player.quest["reeves_quest"] == 5 then
			player.quest["reeves_quest"] = 6
			player:dialogSeq(
				{
					t,
					"Kristal itu meluapkan kekuatan begitu telapak tanganmu menyentuhnya!",
					"Saat kau menarik tangan menahan sakit, tampak serpihan kristal kecil menancap di telapakmu."
				},
				0
			)
			player:addItem("crystal_shard", 1, 0, player.ID)
		end
		player:dialogSeq({t, "Kristal itu tampak tidur."}, 0)
	end),
	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)
		if player.quest["reeves_quest"] >= 4 then
			if speech == "malapetaka" then
				if player.quest["reeves_quest"] == 4 then
					player.quest["reeves_quest"] = 5
				end
				player:dialogSeq(
					{
						t,
						"Kristal itu memancarkan cahaya samar. Kau hampir tergoda menyentuhnya."
					},
					0
				)
			end
		end
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
	end),
}
OxCrystalNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		if player.quest["reeves_quest"] == 5 then
			player.quest["reeves_quest"] = 6
			player:dialogSeq(
				{
					t,
					"Kristal itu meluapkan kekuatan begitu telapak tanganmu menyentuhnya!",
					"Saat kau menarik tangan menahan sakit, tampak serpihan kristal kecil menancap di telapakmu."
				},
				0
			)
			player:addItem("crystal_shard", 1, 0, player.ID)
		end
		player:dialogSeq({t, "Kristal itu tampak tidur."}, 0)
	end),
	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)
		if player.quest["reeves_quest"] >= 4 then
			if speech == "malapetaka" then
				if player.quest["reeves_quest"] == 4 then
					player.quest["reeves_quest"] = 5
				end
				player:dialogSeq(
					{
						t,
						"Kristal itu memancarkan cahaya samar. Kau hampir tergoda menyentuhnya."
					},
					0
				)
			end
		end
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
	end),
}
HorseCrystalNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		if player.quest["reeves_quest"] == 5 then
			player.quest["reeves_quest"] = 6
			player:dialogSeq(
				{
					t,
					"Kristal itu meluapkan kekuatan begitu telapak tanganmu menyentuhnya!",
					"Saat kau menarik tangan menahan sakit, tampak serpihan kristal kecil menancap di telapakmu."
				},
				0
			)
			player:addItem("crystal_shard", 1, 0, player.ID)
		end
		player:dialogSeq({t, "Kristal itu tampak tidur."}, 0)
	end),
	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)
		if player.quest["reeves_quest"] >= 4 then
			if speech == "malapetaka" then
				if player.quest["reeves_quest"] == 4 then
					player.quest["reeves_quest"] = 5
				end
				player:dialogSeq(
					{
						t,
						"Kristal itu memancarkan cahaya samar. Kau hampir tergoda menyentuhnya."
					},
					0
				)
			end
		end
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
	end),
}
PigCrystalNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		if player.quest["reeves_quest"] == 5 then
			player.quest["reeves_quest"] = 6
			player:dialogSeq(
				{
					t,
					"Kristal itu meluapkan kekuatan begitu telapak tanganmu menyentuhnya!",
					"Saat kau menarik tangan menahan sakit, tampak serpihan kristal kecil menancap di telapakmu."
				},
				0
			)
			player:addItem("crystal_shard", 1, 0, player.ID)
		end
		player:dialogSeq({t, "Kristal itu tampak tidur."}, 0)
	end),
	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)
		if player.quest["reeves_quest"] >= 4 then
			if speech == "malapetaka" then
				if player.quest["reeves_quest"] == 4 then
					player.quest["reeves_quest"] = 5
				end
				player:dialogSeq(
					{
						t,
						"Kristal itu memancarkan cahaya samar. Kau hampir tergoda menyentuhnya."
					},
					0
				)
			end
		end
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
	end),
}
RabbitCrystalNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
		if player.quest["reeves_quest"] == 5 then
			player.quest["reeves_quest"] = 6
			player:dialogSeq(
				{
					t,
					"Kristal itu meluapkan kekuatan begitu telapak tanganmu menyentuhnya!",
					"Saat kau menarik tangan menahan sakit, tampak serpihan kristal kecil menancap di telapakmu."
				},
				0
			)
			player:addItem("crystal_shard", 1, 0, player.ID)
		end
		player:dialogSeq({t, "Kristal itu tampak tidur."}, 0)
	end),
	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)
		if player.quest["reeves_quest"] >= 4 then
			if speech == "malapetaka" then
				if player.quest["reeves_quest"] == 4 then
					player.quest["reeves_quest"] = 5
				end
				player:dialogSeq(
					{
						t,
						"Kristal itu memancarkan cahaya samar. Kau hampir tergoda menyentuhnya."
					},
					0
				)
			end
		end
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID
	end),
}
